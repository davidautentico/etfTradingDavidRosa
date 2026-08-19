package com.alphapowertrading.simulator.analysis.sma;

import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

public class SMAMeanRevertingAnalyzer {

  private static final double MINIPIPS_PER_PIP = 10.0;

  private static final ZoneId MADRID_ZONE =
          ZoneId.of("Europe/Madrid");

  public SMAMeanRevertingResult analyze(
          MarketData marketData,
          SMAMeanRevertingParameters parameters) {

    ZoneId zoneId = ZoneId.of("Europe/Madrid");

    int[] localHours = calculateLocalHours(marketData.candles(), zoneId);

    return analyze(marketData, parameters, 0, marketData.size() - 1 ,localHours);
  }

  public SMAMeanRevertingResult analyze(
          MarketData marketData,
          SMAMeanRevertingParameters parameters,
          int startIndex,
          int endIndex,
          int[] localHours) {

    validateRange(marketData, startIndex, endIndex);

    if (marketData.size() == 0) {
      return emptyResult(parameters);
    }

    double[] sma = calculateSma(marketData, parameters.smaLength());
    int[] tradingHours = parameters.tradingHours();

    long distance = toMinipips(parameters.distancePips());
    long tpDistance = toMinipips(parameters.tpPips());
    long slDistance = toMinipips(parameters.slPips());
    double transactionCost = parameters.transactionCostPips();

    PositionState positions = createPositionState(parameters);
    Statistics statistics = new Statistics(endIndex - startIndex + 1);

    for (int index = Math.max(startIndex, parameters.smaLength());
         index <= endIndex;
         index++) {

      Candle candle = marketData.get(index);

      processOpenPositions(
              candle,
              index,
              sma,
              positions,
              statistics,
              transactionCost);

      if (canOpenPosition(candle, index, sma, tradingHours, parameters, distance, localHours)) {
        openPosition(
                candle,
                index,
                parameters,
                positions,
                tpDistance,
                slDistance);
      }
    }

    return buildResult(
            parameters,
            statistics,
            marketData,
            startIndex,
            endIndex);
  }

  private void validateRange(
          MarketData marketData,
          int startIndex,
          int endIndex) {

    if (marketData == null || marketData.size() == 0) {
      return;
    }

    if (startIndex < 0
            || endIndex >= marketData.size()
            || startIndex > endIndex) {

      throw new IllegalArgumentException(
              "Invalid analysis range: "
                      + startIndex
                      + " -> "
                      + endIndex);
    }
  }

  private PositionState createPositionState(
          SMAMeanRevertingParameters parameters) {
    //int capacity = Math.max(1, parameters.exitAfterCandles() + 1);

    int capacity = Math.max(1, 1);

    return new PositionState(capacity);
  }

  private long toMinipips(double pips) {
    return Math.round(pips * MINIPIPS_PER_PIP);
  }

  private boolean canOpenPosition(
          Candle candle,
          int index,
          double[] sma,
          int[] tradingHours,
          SMAMeanRevertingParameters parameters,
          long distance,
          int[] localHours) {

    if (index < parameters.smaLength()) {
      return false;
    }

    if (!isTradingHour(index, localHours, tradingHours)) {
      return false;
    }

    long entryLevel = Math.round(sma[index]) + distance;

    return candle.open() >= entryLevel;
  }

  private void openPosition(
          Candle candle,
          int index,
          SMAMeanRevertingParameters parameters,
          PositionState positions,
          long tpDistance,
          long slDistance) {

    int slot = positions.findFreeSlot();

    if (slot < 0) {
      return;
    }

    long entry = candle.open();

    positions.entryPrices[slot] = entry;
    positions.takeProfits[slot] = entry - tpDistance;
    positions.stopLosses[slot] = entry + slDistance;
    positions.entryIndexes[slot] = index;
    positions.maximumIndexes[slot] =
            index + parameters.exitAfterCandles();
    positions.maes[slot] = 0.0;
    positions.mfes[slot] = 0.0;
    positions.active[slot] = true;
  }

  private void processOpenPositions(
          Candle candle,
          int index,
          double[] sma,
          PositionState positions,
          Statistics statistics,
          double transactionCost) {

    for (int slot = 0; slot < positions.active.length; slot++) {

      if (!positions.active[slot]) {
        continue;
      }

      updateMaeMfe(candle, positions, slot);

      long exitPrice = findExitPrice(
              candle,
              index,
              sma,
              positions,
              slot);

      if (exitPrice == Long.MIN_VALUE) {
        continue;
      }

      closePosition(
              exitPrice,
              slot,
              positions,
              statistics,
              transactionCost);
    }
  }

  private void updateMaeMfe(
          Candle candle,
          PositionState positions,
          int slot) {

    long entry = positions.entryPrices[slot];

    double adverse =
            (candle.high() - entry)
                    / MINIPIPS_PER_PIP;

    double favorable =
            (entry - candle.low())
                    / MINIPIPS_PER_PIP;

    positions.maes[slot] =
            Math.max(positions.maes[slot], adverse);

    positions.mfes[slot] =
            Math.max(positions.mfes[slot], favorable);
  }

  private long findExitPrice(
          Candle candle,
          int index,
          double[] sma,
          PositionState positions,
          int slot) {

    long open = candle.open();

    if (open >= positions.stopLosses[slot]) {
      return open;
    }

    if (candle.high() >= positions.stopLosses[slot]) {
      return positions.stopLosses[slot];
    }

    if (open <= positions.takeProfits[slot]) {
      return open;
    }

    if (candle.low() <= positions.takeProfits[slot]) {
      return positions.takeProfits[slot];
    }

    if (open < sma[index]) {
      return open;
    }

    if (index >= positions.maximumIndexes[slot]) {
      return open;
    }

    return Long.MIN_VALUE;
  }

  private void closePosition(
          long exitPrice,
          int slot,
          PositionState positions,
          Statistics statistics,
          double transactionCost) {

    double grossPips =
            (positions.entryPrices[slot] - exitPrice)
                    / MINIPIPS_PER_PIP;

    double netPips = grossPips - transactionCost;

    statistics.add(
            netPips,
            positions.maes[slot],
            positions.mfes[slot]);

    positions.active[slot] = false;
    positions.maes[slot] = 0.0;
    positions.mfes[slot] = 0.0;
  }

  private double[] calculateSma(
          MarketData marketData,
          int length) {

    double[] sma = new double[marketData.size()];

    if (marketData.size() <= length) {
      return sma;
    }

    long sum = 0L;

    for (int index = 0; index < length; index++) {
      sum += marketData.get(index).close();
    }

    for (int index = length; index < marketData.size(); index++) {

      sma[index] = (double) sum / length;

      sum -= marketData.get(index - length).close();
      sum += marketData.get(index).close();
    }

    return sma;
  }

  private SMAMeanRevertingResult buildResult(
          SMAMeanRevertingParameters parameters,
          Statistics statistics,
          MarketData marketData,
          int startIndex,
          int endIndex) {

    if (statistics.operations == 0) {
      return emptyResult(parameters);
    }

    double[] returns =
            Arrays.copyOf(statistics.returns, statistics.operations);

    double[] maes =
            Arrays.copyOf(statistics.tradeMaes, statistics.operations);

    double[] mfes =
            Arrays.copyOf(statistics.tradeMfes, statistics.operations);

    double averagePips =
            statistics.totalPips / statistics.operations;

    double medianPips =
            calculateMedian(returns);

    double averageMae =
            statistics.totalMae / statistics.operations;

    double averageMfe =
            statistics.totalMfe / statistics.operations;

    double winRate =
            100.0
                    * statistics.winningOperations
                    / statistics.operations;

    double profitFactor =
            calculateProfitFactor(
                    statistics.grossProfit,
                    statistics.grossLoss);

    double sharpe =
            calculateSharpe(
                    returns,
                    statistics.operations);

    double operationsPerYear =
            calculateOperationsPerYear(
                    statistics.operations,
                    marketData,
                    startIndex,
                    endIndex);

    return new SMAMeanRevertingResult(
            parameters,
            statistics.operations,
            operationsPerYear,
            statistics.winningOperations,
            winRate,
            statistics.totalPips,
            averagePips,
            medianPips,

            averageMae,
            calculatePercentile(maes, 0.50),
            calculatePercentile(maes, 0.75),
            calculatePercentile(maes, 0.90),
            calculatePercentile(maes, 0.95),
            calculatePercentile(maes, 0.99),

            averageMfe,
            calculatePercentile(mfes, 0.50),
            calculatePercentile(mfes, 0.75),
            calculatePercentile(mfes, 0.90),
            calculatePercentile(mfes, 0.95),
            calculatePercentile(mfes, 0.99),

            profitFactor,
            sharpe,
            statistics.maxDrawdown);
  }

  private double calculateProfitFactor(
          double grossProfit,
          double grossLoss) {

    if (grossLoss == 0.0) {
      return grossProfit > 0.0
              ? Double.POSITIVE_INFINITY
              : 0.0;
    }

    return grossProfit / grossLoss;
  }

  private double calculateMedian(double[] values) {
    return calculatePercentile(values, 0.50);
  }

  private double calculatePercentile(
          double[] values,
          double percentile) {

    if (values.length == 0) {
      return 0.0;
    }

    double[] sorted =
            Arrays.copyOf(values, values.length);

    Arrays.sort(sorted);

    if (sorted.length == 1) {
      return sorted[0];
    }

    double position =
            percentile * (sorted.length - 1);

    int lower = (int) Math.floor(position);
    int upper = (int) Math.ceil(position);

    if (lower == upper) {
      return sorted[lower];
    }

    double weight = position - lower;

    return sorted[lower]
            + weight * (sorted[upper] - sorted[lower]);
  }

  private double calculateSharpe(
          double[] returns,
          int size) {

    if (size < 2) {
      return 0.0;
    }

    double sum = 0.0;

    for (int index = 0; index < size; index++) {
      sum += returns[index];
    }

    double mean = sum / size;
    double variance = 0.0;

    for (int index = 0; index < size; index++) {
      double difference = returns[index] - mean;
      variance += difference * difference;
    }

    variance /= size - 1;

    double standardDeviation = Math.sqrt(variance);

    if (standardDeviation == 0.0) {
      return 0.0;
    }

    return mean / standardDeviation * Math.sqrt(252.0);
  }

  private double calculateOperationsPerYear(
          int operations,
          MarketData marketData,
          int startIndex,
          int endIndex) {

    if (operations == 0 || startIndex >= endIndex) {
      return 0.0;
    }

    Duration duration =
            Duration.between(
                    marketData.get(startIndex).date(),
                    marketData.get(endIndex).date());

    long seconds = duration.getSeconds();

    if (seconds <= 0) {
      return 0.0;
    }

    double years =
            seconds / (365.25 * 24.0 * 60.0 * 60.0);

    return operations / years;
  }

  private boolean isTradingHour(
          int candleIndex,
          int[] localHours,
          int[] tradingHours) {

    int hour = localHours[candleIndex];

    return tradingHours[hour] == 1;
  }

  private SMAMeanRevertingResult emptyResult(
          SMAMeanRevertingParameters parameters) {

    return new SMAMeanRevertingResult(
            parameters,
            0,
            0.0,
            0,
            0.0,
            0.0,
            0.0,
            0.0,

            // MAE
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,

            // MFE
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,

            // Risk
            0.0,
            0.0,
            0.0);
  }

  private static final class PositionState {

    private final long[] entryPrices;
    private final long[] takeProfits;
    private final long[] stopLosses;
    private final int[] entryIndexes;
    private final int[] maximumIndexes;
    private final double[] maes;
    private final double[] mfes;
    private final boolean[] active;

    private PositionState(int capacity) {
      entryPrices = new long[capacity];
      takeProfits = new long[capacity];
      stopLosses = new long[capacity];
      entryIndexes = new int[capacity];
      maximumIndexes = new int[capacity];
      maes = new double[capacity];
      mfes = new double[capacity];
      active = new boolean[capacity];
    }

    private int findFreeSlot() {

      for (int slot = 0; slot < active.length; slot++) {
        if (!active[slot]) {
          return slot;
        }
      }

      return -1;
    }
  }

  private static final class Statistics {

    private final double[] returns;
    private final double[] tradeMaes;
    private final double[] tradeMfes;

    private int operations;
    private int winningOperations;

    private double totalPips;
    private double totalMae;
    private double totalMfe;

    private double grossProfit;
    private double grossLoss;

    private double equity;
    private double peak;
    private double maxDrawdown;

    private Statistics(int capacity) {
      returns = new double[capacity];
      tradeMaes = new double[capacity];
      tradeMfes = new double[capacity];
    }

    private void add(
            double netPips,
            double mae,
            double mfe) {

      returns[operations] = netPips;
      tradeMaes[operations] = mae;
      tradeMfes[operations] = mfe;
      operations++;

      totalPips += netPips;
      totalMae += mae;
      totalMfe += mfe;

      if (netPips > 0.0) {
        winningOperations++;
        grossProfit += netPips;
      } else if (netPips < 0.0) {
        grossLoss += -netPips;
      }

      updateDrawdown(netPips);
    }

    private void updateDrawdown(double netPips) {

      equity += netPips;
      peak = Math.max(peak, equity);

      double drawdown = peak - equity;
      maxDrawdown = Math.max(maxDrawdown, drawdown);
    }
  }

  private int[] calculateLocalHours(
          List<Candle> candles,
          ZoneId zoneId) {

    int[] hours = new int[candles.size()];

    for (int i = 0; i < candles.size(); i++) {
      hours[i] =
              candles.get(i)
                      .date()
                      .atZone(ZoneOffset.UTC)
                      .withZoneSameInstant(zoneId)
                      .getHour();
    }

    return hours;
  }
}