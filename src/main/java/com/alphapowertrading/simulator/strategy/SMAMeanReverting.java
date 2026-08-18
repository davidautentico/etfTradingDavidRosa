package com.alphapowertrading.simulator.strategy;

import com.alphapowertrading.simulator.core.broker.Broker;
import com.alphapowertrading.simulator.core.broker.BuyType;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketContext;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.strategy.Strategy;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component("smaMeanReverting")
public class SMAMeanReverting implements Strategy {

  private static final long MINIPIPS_PER_PIP = 10;

  private final int smaLength;
  private final double distancePips;
  private final double tpPips;
  private final double slPips;

  /*
   * Posiciones 0..23 correspondientes a las horas del día.
   * 1 = se permite abrir operaciones.
   * 0 = no se permite abrir operaciones.
   */
  private final int[] tradingHours;

  public SMAMeanReverting() {
    this(
        72,
        15,
        1500,
        3000,
        new int[] {
          0, 0, 0, 0, 1, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0
        });
  }

  public SMAMeanReverting(
      int smaLength, double distancePips, double tpPips, double slPips, int[] tradingHours) {

    if (smaLength <= 0) {
      throw new IllegalArgumentException("SMA length must be greater than zero");
    }

    if (distancePips < 0) {
      throw new IllegalArgumentException("Distance cannot be negative");
    }

    if (tpPips <= 0) {
      throw new IllegalArgumentException("TP must be greater than zero");
    }

    if (slPips <= 0) {
      throw new IllegalArgumentException("SL must be greater than zero");
    }

    if (tradingHours == null || tradingHours.length != 24) {
      throw new IllegalArgumentException("Trading hours array must contain exactly 24 positions");
    }

    for (int value : tradingHours) {
      if (value != 0 && value != 1) {
        throw new IllegalArgumentException("Trading hours values must be 0 or 1");
      }
    }

    this.smaLength = smaLength;
    this.distancePips = distancePips;
    this.tpPips = tpPips;
    this.slPips = slPips;
    this.tradingHours = tradingHours.clone();
  }

  @Override
  public void onCandle(MarketContext context, Broker broker) {
    Candle candle = context.candle();

    if (broker.hasOpenPosition()) {
      managePosition(context, broker);
      return;
    }

    if (!hasEnoughHistory(context)) {
      return;
    }

    /*
     * Solo permitimos abrir nuevas posiciones
     * durante las horas habilitadas.
     */
    if (!isTradingHour(candle)) {
      return;
    }

    double sma = calculateSma(context);

    long distance = pipsToMinipips(distancePips);
    // System.out.println("sma + pips: " + sma + " " + distance);

    long shortEntryLevel = (long) Math.ceil(sma + distance);

    /*
     * Mean reversion SHORT:
     *
     * Entramos solamente cuando el OPEN está
     * suficientemente por encima de la SMA.
     */
    if (candle.open() >= shortEntryLevel) {
      openShort(context, broker);
    }
  }

  private void managePosition(MarketContext context, Broker broker) {
    if (broker.position().side()
        != com.alphapowertrading.simulator.core.broker.PositionSide.SHORT) {
      return;
    }

    Candle candle = context.candle();

    long entry = broker.position().entryPrice();

    long tp = entry - pipsToMinipips(tpPips);

    long sl = entry + pipsToMinipips(slPips);

    // SL por apertura
    if (candle.open() >= sl) {
      broker.buyToCover(candle.date(), candle.open(), "SL_OPEN_SHORT");
      return;
    }

    // SL intrabar
    if (candle.high() >= sl) {
      broker.buyToCover(candle.date(), sl, "SL_SHORT");
      return;
    }

    // TP por apertura
    if (candle.open() <= tp) {
      broker.buyToCover(candle.date(), candle.open(), "TP_OPEN_SHORT");
      return;
    }

    // TP intrabar
    if (candle.low() <= tp) {
      broker.buyToCover(candle.date(), tp, "TP_SHORT");
      return;
    }

    int indexDif = context.index() - broker.position().index();

    if (indexDif == 48) {
        broker.buyToCover(candle.date(), candle.open(), "DIFF");
        return;
    }

  }

  private boolean isTradingHour(Candle candle) {
    int hour = candle.date().getHour();
    return tradingHours[hour] == 1;
  }

  private void openShort(MarketContext context, Broker broker) {
    Candle candle = context.candle();

    int quantity = 1;

    broker.shortSell(candle.date(), candle.open(), quantity, context.index(), BuyType.NO_LUNES);
  }

  private double calculateSma(MarketContext context) {
    MarketData marketData = context.marketData();

    int index = context.index();

    long sum = 0;

    for (int i = index - smaLength; i < index; i++) {
      sum += marketData.get(i).close();
    }

    return (double) sum / smaLength;
  }

  private boolean hasEnoughHistory(MarketContext context) {
    return context.index() >= smaLength;
  }

  private long pipsToMinipips(double pips) {
    return Math.round(pips * MINIPIPS_PER_PIP);
  }

  private boolean isLastDayOfWeek(MarketData marketData, int index) {
    if (index >= marketData.size() - 1) {
      return true;
    }

    LocalDate current = marketData.get(index).date().toLocalDate();

    LocalDate next = marketData.get(index + 1).date().toLocalDate();

    return current.getDayOfWeek().getValue() >= 5 || !current.plusDays(1).equals(next);
  }
}
