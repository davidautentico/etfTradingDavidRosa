package com.alphapowertrading.simulator.core.engine;

import com.alphapowertrading.simulator.core.broker.Broker;
import com.alphapowertrading.simulator.core.broker.PositionSide;
import com.alphapowertrading.simulator.core.broker.Trade;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketContext;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.market.MarketState;
import com.alphapowertrading.simulator.core.report.BacktestReport;
import com.alphapowertrading.simulator.core.strategy.Strategy;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public class BacktestEngine {

  private final double initialCapital;
  private final boolean showTrades;
  private final boolean showLossWeek;
  private final double lossWeekThreshold;
  private final boolean showMaxDd;
  private final double commissionRate;
  private final double spread;
  private final LocalDate startDate;
  private final LocalDate endDate;

  public BacktestEngine(
      double initialCapital,
      boolean showTrades,
      boolean showLossWeek,
      double lossWeekThreshold,
      boolean showMaxDd,
      double spread,
      LocalDate startDate,
      LocalDate endDate) {
    this(
        initialCapital,
        showTrades,
        showLossWeek,
        lossWeekThreshold,
        showMaxDd,
        0.0,
        spread,
        startDate,
        endDate);
  }

  public BacktestEngine(
      double initialCapital,
      boolean showTrades,
      boolean showLossWeek,
      double lossWeekThreshold,
      boolean showMaxDd,
      double commissionRate,
      double spread,
      LocalDate startDate,
      LocalDate endDate) {
    if (commissionRate < 0) {
      throw new IllegalArgumentException("Commission rate cannot be negative");
    }
    if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
      throw new IllegalArgumentException(
          "Start date cannot be after end date: " + startDate + " > " + endDate);
    }

    this.initialCapital = initialCapital;
    this.showTrades = showTrades;
    this.showLossWeek = showLossWeek;
    this.lossWeekThreshold = lossWeekThreshold;
    this.showMaxDd = showMaxDd;
    this.commissionRate = commissionRate;
    this.spread = spread;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  public BacktestReport run(MarketData marketData, Strategy strategy) {
    if (marketData == null || marketData.size() == 0) {
      throw new IllegalArgumentException("Market data is empty");
    }

    int startIndex = findStartIndex(marketData);
    int endIndex = findEndIndex(marketData);

    if (startIndex > endIndex) {
      throw new IllegalArgumentException(
          "No market data available in simulation range: "
              + startDate
              + " -> "
              + endDate);
    }

    Broker broker = new Broker(initialCapital, showTrades, commissionRate, spread);

    Candle firstCandle = marketData.get(startIndex);

    MarketState marketState =
        new MarketState(firstCandle.close(), firstCandle.date().toLocalDate());

    strategy.initialize(broker, marketData);

    for (int i = startIndex; i <= endIndex; i++) {
      Candle candle = marketData.get(i);

      marketState.update(candle.close(), candle.date().toLocalDate());

      MarketContext context =
          new MarketContext(
              candle,
              marketData,
              i,
              marketState.currentDrawdown(candle.close()),
              marketState.currentLowDrawdown(candle.low()),
              marketState.peakClose(),
              marketState.peakDate(),
              startIndex,
              endIndex);

      int tradesBefore = broker.trades().size();

      strategy.onCandle(context, broker);

      checkClosedTrade(broker, tradesBefore, marketData);

      boolean newMaxDd = broker.updateDrawdown(candle.date(), candle.low());

      if (showMaxDd && newMaxDd) {
        printMaxDrawdown(candle, broker);
      }

      broker.recordEquity(candle.close());
    }

    Candle lastCandle = marketData.get(endIndex);

    int tradesBeforeFinish = broker.trades().size();

    strategy.finish(lastCandle, marketData, broker);

    checkClosedTrade(broker, tradesBeforeFinish, marketData);

    return broker.buildReport(lastCandle.close(), lastCandle.date());
  }

  private int findStartIndex(MarketData marketData) {
    if (startDate == null) {
      return 0;
    }

    for (int i = 0; i < marketData.size(); i++) {
      if (!marketData.get(i).date().toLocalDate().isBefore(startDate)) {
        return i;
      }
    }

    return marketData.size();
  }

  private int findEndIndex(MarketData marketData) {
    if (endDate == null) {
      return marketData.size() - 1;
    }

    for (int i = marketData.size() - 1; i >= 0; i--) {
      if (!marketData.get(i).date().toLocalDate().isAfter(endDate)) {
        return i;
      }
    }

    return -1;
  }

  private void checkClosedTrade(Broker broker, int tradesBefore, MarketData marketData) {
    if (!showLossWeek || broker.trades().size() <= tradesBefore) {
      return;
    }

    Trade trade = broker.trades().getLast();

    if (pnlPercentage(trade) <= lossWeekThreshold) {
      printLossWeek(trade, marketData);
    }
  }

  private double pnlPercentage(Trade trade) {
    if (trade.side() == PositionSide.SHORT) {
      return ((double) trade.entryPrice() / trade.exitPrice() - 1) * 100;
    }

    return ((double) trade.exitPrice() / trade.entryPrice() - 1) * 100;
  }

  private void printMaxDrawdown(Candle candle, Broker broker) {
    System.out.println();
    System.out.println("========== NEW MAX DRAWDOWN ==========");

    System.out.printf(
        "Date: %s | Low: %.2f | MaxDD: %.2f%%%n",
        candle.date(), candle.low() * 0.01, broker.maxDrawdown() * 100);

    System.out.printf(
        "Peak Equity: %.2f | Equity at Low: %.2f%n",
        broker.peakEquity(), broker.equity(candle.low()));

    System.out.println("=======================================");
  }

  private void printLossWeek(Trade trade, MarketData marketData) {
    LocalDate date = trade.exitDate().toLocalDate();

    LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    LocalDate friday = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));

    System.out.println();

    System.out.printf(
        "LOSS WEEK | Side: %s | PnL: %.2f%% | Reason: %s%n",
        trade.side(), pnlPercentage(trade), trade.closeReason());

    System.out.printf(
        "Trade: %s -> %s | Week: %s -> %s%n", trade.entryDate(), trade.exitDate(), monday, friday);

    System.out.printf("%-12s %10s %10s %10s %10s%n", "Date", "Open", "High", "Low", "Close");

    for (Candle candle : marketData.candles()) {
      if (!candle.date().isBefore(monday.atStartOfDay())
          && !candle.date().isAfter(friday.atStartOfDay())) {
        System.out.printf(
            "%-12s %10.2f %10.2f %10.2f %10.2f%n",
            candle.date(),
            candle.open() * 0.01,
            candle.high() * 0.01,
            candle.low() * 0.01,
            candle.close() * 0.01);
      }
    }

    System.out.println();
  }
}
