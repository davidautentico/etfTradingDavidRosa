package com.alphapowertrading.simulator.strategy;

import com.alphapowertrading.simulator.core.broker.Broker;
import com.alphapowertrading.simulator.core.broker.BuyType;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketContext;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.strategy.Strategy;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Random;

@Component("fleuryv4")
public class OPPWFleuryV4Strategy implements Strategy {

    private static final double ENTRY_SLIPPAGE = 0.000;
    private static final double ENTRY_BIAS = 0.000;
  private static final double CURRENT_LOSS = 0.00;
  private static final double WEEKLY_CLOSE_PROBABILITY = 1.0;

  private final double tp;
  private final double tph;
  private final double sl;
  private final double openGap;
  private final Random random = new Random(12345L);

  public OPPWFleuryV4Strategy () {
    this(0.016, 0.99, 0.035, 0.99);
  }

  public OPPWFleuryV4Strategy (double tp, double tph, double sl, double openGap) {

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


    if (context.index()>=1) {
      double openDiffPer = (double) (candle.open() - ycandle.close()) / ycandle.close();
      if (isMonday(candle)
              && ! candle.date().equals(LocalDate.of(2020, 11, 9))
      ) {
        if (openDiffPer >= 0.00) {
          buy(context, context.index(), candle.open(), 0.6, broker, BuyType.LUNES);
        }
        if (openDiffPer < 0){
          buy(context, context.index(), candle.open(), 1.0, broker, BuyType.LUNES);
        }
      }
    }
  }

  private void managePosition(MarketContext context, Broker broker) {

    Candle candle = context.candle();
    Candle ycandle = context.marketData().get(context.index()-1);
    long yOpen = ycandle.open();
    long yClose = ycandle.close();
    long todayOpen = candle.open();
    long todayLow = candle.low();
    long todayHigh = candle.high();
    long todayClose = candle.close();
    long entry = broker.position().entryPrice();
    DayOfWeek dayOfWeek = candle.date().getDayOfWeek();
    double actualProfitPer = (double) (candle.open() - entry) / entry;
    double actualCloseProfitPer = (double) (candle.close() - entry) / entry;
    long targetTp = (long) (entry*(1+tp));
    long targetSl = (long) (entry*(1-sl));
    long actualPositionEntry = broker.position().entryPrice();

    //1.- OPEN TP
    if (broker.hasOpenPosition() && todayOpen >= targetTp){
      targetTp = (long) (todayOpen*1.00);
      if (todayHigh>=targetTp) {
        broker.sell(candle.date(), targetTp, "OPEN TP " + dayOfWeek);
      }
    }

    //1. TP if losses yet on thursday or friday
    if (broker.hasOpenPosition() && todayOpen < entry
            && dayOfWeek.ordinal()>=DayOfWeek.THURSDAY.ordinal()){
      targetTp = (long) (todayOpen*1.01);
      if (todayHigh>=targetTp) {
        broker.sell(candle.date(), targetTp, "OPEN<ENTRY TP " + dayOfWeek);
      }
    }

    //2. CLOSE SL
    if (broker.hasOpenPosition() && todayClose < targetSl
    ) {
      broker.sell(candle.date(), todayClose, "SL CLOSE " + dayOfWeek);
    }

    //3. TUESDAY REENTRY
    double profitFromMonday = (double) (todayClose - entry) /entry;
    if (!broker.hasOpenPosition()
            && profitFromMonday<-0.00
            && candle.date().getDayOfWeek()==DayOfWeek.TUESDAY){
      buy(context, context.index(), candle.close(), 1.0, broker, BuyType.NO_LUNES);
    }

    //4. Friday's close
    if (broker.hasOpenPosition()) {
        if (candle.date().getDayOfWeek()==DayOfWeek.FRIDAY) {
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

    private void buy(MarketContext context, int index, long reentry, double shareFactor, Broker broker, BuyType buyType) {

        Candle candle = context.candle();

        double price = reentry * 0.01;
        int shares = (int) (broker.cash() * shareFactor / price);

        if (shares > 0) {
            broker.buy(candle.date(),index, reentry, shares, buyType);
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
