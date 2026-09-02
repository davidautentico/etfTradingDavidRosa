package com.alphapowertrading.simulator.strategy;

import com.alphapowertrading.simulator.core.broker.Broker;
import com.alphapowertrading.simulator.core.broker.BuyType;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketContext;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.strategy.Strategy;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Random;

import org.springframework.stereotype.Component;

@Component("fleuryv2")
public class OPPWFleuryV2Strategy implements Strategy {

    private static final double ENTRY_SLIPPAGE = 0.000;
    private static final double ENTRY_BIAS = 0.00;
  private static final double CURRENT_LOSS = 0.00;
  private static final double WEEKLY_CLOSE_PROBABILITY = 1.0;

  private final double tp;
  private final double tph;
  private final double sl;
  private final double openGap;
  private final Random random = new Random(12345L);

  public OPPWFleuryV2Strategy() {
    this(0.035, 0.035, 0.99, 0.005);
  }

  public OPPWFleuryV2Strategy(double tp, double tph, double sl, double openGap) {

    this.tp = tp;
    this.tph = tph;
    this.sl = sl;
    this.openGap = openGap;
  }

  @Override
  public void initialize(Broker broker, MarketData marketData) {
    random.setSeed(12345L);
  }

  @Override
  public void onCandle(MarketContext context, Broker broker) {
    Candle candle = context.candle();
    Candle ycandle = null;

    if (context.isFirstCandle()) return;


    if (broker.hasOpenPosition()) {
      managePosition(context, broker);
      return;
    }

    if (!context.isFirstCandle()){
      ycandle = context.marketData().get(context.index()-1);
    }


    long entryThr = (long) (candle.open()*(1+ENTRY_BIAS));
    long entryLowThr = (long) (candle.open()*(1-ENTRY_BIAS));
    double openDiffPer = (double) (candle.open() - ycandle.close()) /ycandle.close();
    if (isMonday(candle)
            && !candle.date().equals(LocalDate.of(2020, 11, 9))
    ) {
      if (openDiffPer>=0 && candle.low() <= entryLowThr) {
        buy(context, (long) (entryLowThr * (1 + ENTRY_SLIPPAGE)), 0.6, broker, BuyType.LUNES);
      }
        if (openDiffPer<0 && candle.high() >= entryThr) {
            buy(context, (long) (entryThr * (1 + ENTRY_SLIPPAGE)), 1, broker, BuyType.LUNES);
        }
    }
  }

  private void managePosition(MarketContext context, Broker broker) {

    Candle candle = context.candle();
    long entry = broker.position().entryPrice();
    DayOfWeek dayOfWeek = candle.date().getDayOfWeek();
    double actualProfitPer = (double) (candle.open() - entry) / entry;
    double gapPer = calculateGap(context);

    //1. Gap close
    if (broker.hasOpenPosition() && shouldCloseByGap(actualProfitPer, gapPer)) {
      broker.sell(candle.date(), candle.open(), "GAP " + dayOfWeek);
    }

    //2. Open TP close
    if (broker.hasOpenPosition() && hasEntryTp(candle, entry)) {
      closeAtEntryTp(context, broker, candle, dayOfWeek);
    }

    //3. Hard DAY SL -> intraday STOP
    if (broker.hasOpenPosition() && hitsLowSl(candle, entry)) {
      long slPrice = calculateSlPrice(entry);
      broker.sell(candle.date(), slPrice, "SLL " + dayOfWeek);
    }

    //4. Hard TP
    if (broker.hasOpenPosition() && hitsHighTp(candle, entry)) {
      long tpPrice = calculateTphPrice(entry);
      broker.sell(candle.date(), tpPrice, "TPH " + dayOfWeek);
    }

    //5. Should Open a new position if closed by 1-4
    if (!broker.hasOpenPosition()) {
      double closeDiff = (candle.close() - candle.open()) / (double) candle.open();
      if (closeDiff>-0.04 && !isLastDayOfWeek(context.marketData(), context.index())) {//si no es una pérdida menor del -1%
        //buy(context, candle.close(), broker, BuyType.NO_LUNES);
      }
    }

  //6. Should close if weak day
  if (broker.hasOpenPosition()) {
      if (candle.high()==candle.open()) {//si no es una pérdida menor del -1%
          //broker.sell(candle.date(), candle.close(), "WEAK " + dayOfWeek);
      }
  }

    //7. Friday's close
    if (broker.hasOpenPosition() && shouldCloseWeekly(context.marketData(), context.index())) {
      if (random.nextDouble() < WEEKLY_CLOSE_PROBABILITY) {
        broker.sell(candle.date(), candle.close(), "WEEKLY_CLOSE");
      }
    }
  }

  private double calculateGap(MarketContext context) {

    if (context.isFirstCandle()) {
      return 0.0;
    }

    Candle candle = context.candle();
    Candle previous = context.marketData().get(context.index() - 1);

    return (double) (candle.open() - previous.close()) / previous.close();
  }

  private boolean shouldCloseByGap(double actualProfitPer, double gapPer) {

    return actualProfitPer < CURRENT_LOSS && gapPer >= openGap;
  }

  private boolean hasEntryTp(Candle candle, long entry) {

    long tpPrice = calculateTpPrice(entry);

    return candle.open() >= tpPrice;
  }

  private void closeAtEntryTp(MarketContext context, Broker broker, Candle candle, DayOfWeek dayOfWeek) {

    broker.sell(candle.date(), candle.open(), "OTP " + dayOfWeek);

  }

  private boolean hitsLowSl(Candle candle, long entry) {

    long slPrice = calculateSlPrice(entry);

    return candle.open() > slPrice && candle.low() <= slPrice;
  }

  private boolean hitsHighTp(Candle candle, long entry) {

    long tpPrice = calculateTphPrice(entry);

    return candle.open() < tpPrice && candle.high() >= tpPrice;
  }

  private long calculateTpPrice(long entry) {
    return (long) (entry * (1 + tp));
  }

  private long calculateTphPrice(long entry) {
    return (long) (entry * (1 + tph));
  }

  private long calculateSlPrice(long entry) {
    return (long) (entry * (1 - sl));
  }

  private boolean shouldCloseWeekly(MarketData marketData, int index) {

    return isLastDayOfWeek(marketData, index);
  }

  private void buy(MarketContext context, long reentry, Broker broker, BuyType buyType) {

    Candle candle = context.candle();
    double allocation = allocationForDrawdown(context.drawdown());

    double price = reentry * 0.01;
    int shares = (int) (broker.cash() * allocation / price);

    if (shares > 0) {
      broker.buy(candle.date(), reentry, shares, buyType);
    }
  }

    private void buy(MarketContext context, long reentry, double shareFactor, Broker broker, BuyType buyType) {

        Candle candle = context.candle();

        double price = reentry * 0.01;
        int shares = (int) (broker.cash() * shareFactor / price);

        if (shares > 0) {
            broker.buy(candle.date(), reentry, shares, buyType);
        }
    }

  private void buy(MarketContext context, Broker broker) {

    Candle candle = context.candle();
    double allocation = allocationForDrawdown(context.drawdown());

    double price = candle.open() * 0.01;
    int shares = (int) (broker.cash() * allocation / price);

    if (shares > 0) {
      broker.buy(candle.date(), candle.open(), shares, BuyType.LUNES);
    }
  }

  private double allocationForDrawdown(double drawdown) {
    return 1.00;
  }

  private boolean isMonday(Candle candle) {
    return candle.date().getDayOfWeek() == DayOfWeek.MONDAY;
  }

  private boolean isLastDayOfWeek(MarketData marketData, int index) {

    if (index >= marketData.size() - 1) {
      return true;
    }

    LocalDate current = LocalDate.from(marketData.get(index).date());

    LocalDate next = LocalDate.from(marketData.get(index + 1).date());

    return current.getDayOfWeek() == DayOfWeek.FRIDAY || !current.plusDays(1).equals(next);
  }
}
