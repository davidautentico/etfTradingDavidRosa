package com.alphapowertrading.simulator.analysis.sma.walkforward;

import com.alphapowertrading.simulator.analysis.sma.SMAMeanRevertingAnalyzer;
import com.alphapowertrading.simulator.analysis.sma.SMAMeanRevertingParameters;
import com.alphapowertrading.simulator.analysis.sma.SMAMeanRevertingResult;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Performs rolling walk-forward analysis for the SMA mean-reverting strategy.
 *
 * <p>The default configuration uses 12 months of in-sample data, followed by one month of
 * out-of-sample data, advancing one month at a time.
 *
 * <p>The implementation deliberately avoids streams and unnecessary MarketData allocations inside
 * the parameter sweep because this analyzer can execute millions of strategy evaluations.
 */
public class SMAMeanRevertingWalkForwardAnalyzer {

  private final SMAMeanRevertingAnalyzer analyzer;

  private final int inSampleMonths;
  private final int outOfSampleMonths;
  private final int stepMonths;

  private final int[] smaLengths;
  private final double[] distances;
  private final double[] tps;
  private final double[] sls;
  private final int[] exits;
  private final double[] transactionCosts;

  private final double minimumOperations;
  private final double minimumAveragePips;
  private final double minimumProfitFactor;

  /** Creates the default walk-forward configuration. */
  public SMAMeanRevertingWalkForwardAnalyzer() {
    this(
        24,
        1,
        1,
        new int[] {12, 24, 36, 48, 60, 72, 84},
        new double[] {10, 15, 20},
        new double[] {999},
        new double[] {999},
        new int[] {12,24,36,48,60,72},
        new double[] {0.0},
        20,
        2.0,
        1.30);
  }

  /**
   * Creates a walk-forward analyzer.
   *
   * @param inSampleMonths IS duration
   * @param outOfSampleMonths OOS duration
   * @param stepMonths rolling step
   * @param smaLengths SMA values
   * @param distances entry distances
   * @param tps take-profit values
   * @param sls stop-loss values
   * @param exits maximum holding periods
   * @param transactionCosts transaction costs
   * @param minimumOperations minimum IS operations
   * @param minimumAveragePips minimum IS average pips
   * @param minimumProfitFactor minimum IS profit factor
   */
  public SMAMeanRevertingWalkForwardAnalyzer(
      int inSampleMonths,
      int outOfSampleMonths,
      int stepMonths,
      int[] smaLengths,
      double[] distances,
      double[] tps,
      double[] sls,
      int[] exits,
      double[] transactionCosts,
      double minimumOperations,
      double minimumAveragePips,
      double minimumProfitFactor) {

    if (inSampleMonths <= 0 || outOfSampleMonths <= 0 || stepMonths <= 0) {
      throw new IllegalArgumentException("Walk-forward periods must be greater than zero");
    }

    if (smaLengths == null || smaLengths.length == 0) {
      throw new IllegalArgumentException("SMA lengths cannot be empty");
    }

    if (distances == null || distances.length == 0) {
      throw new IllegalArgumentException("Distances cannot be empty");
    }

    if (tps == null || tps.length == 0) {
      throw new IllegalArgumentException("TP values cannot be empty");
    }

    if (sls == null || sls.length == 0) {
      throw new IllegalArgumentException("SL values cannot be empty");
    }

    if (exits == null || exits.length == 0) {
      throw new IllegalArgumentException("Exit values cannot be empty");
    }

    if (transactionCosts == null || transactionCosts.length == 0) {
      throw new IllegalArgumentException("Transaction costs cannot be empty");
    }

    this.analyzer = new SMAMeanRevertingAnalyzer();

    this.inSampleMonths = inSampleMonths;
    this.outOfSampleMonths = outOfSampleMonths;
    this.stepMonths = stepMonths;

    this.smaLengths = smaLengths.clone();
    this.distances = distances.clone();
    this.tps = tps.clone();
    this.sls = sls.clone();
    this.exits = exits.clone();
    this.transactionCosts = transactionCosts.clone();

    this.minimumOperations = minimumOperations;
    this.minimumAveragePips = minimumAveragePips;
    this.minimumProfitFactor = minimumProfitFactor;
  }

  /**
   * Runs walk-forward analysis for one trading hour.
   *
   * <p>The market data is assumed to be sorted chronologically.
   *
   * @param marketData complete market data
   * @param hour trading hour
   * @param initialEquity initial hypothetical equity
   * @param pipValue pip value used for the equity curve
   * @return walk-forward results
   */
  public List<WalkForwardResult> analyzeHour(
      MarketData marketData, int hour, double initialEquity, double pipValue) {

    if (marketData == null || marketData.size() == 0) {
      throw new IllegalArgumentException("Market data cannot be empty");
    }

    if (hour < 0 || hour > 23) {
      throw new IllegalArgumentException("Hour must be between 0 and 23");
    }

    List<WalkForwardResult> results = new ArrayList<>();

    LocalDateTime firstDate = marketData.get(0).date();
    LocalDateTime lastDate = marketData.get(marketData.size() - 1).date();

    double equity = initialEquity;
    double cumulativePips = 0.0;

    LocalDateTime isStart = firstDate;
    int windowNumber = 1;

    /*
     * Build and process each window directly.
     *
     * There is intentionally no intermediate List<WalkForwardWindow>.
     */
    while (true) {

      LocalDateTime isEnd = isStart.plusMonths(inSampleMonths);

      LocalDateTime oosStart = isEnd;

      LocalDateTime oosEnd = oosStart.plusMonths(outOfSampleMonths);

      if (oosEnd.isAfter(lastDate)) {
        break;
      }

      /*
       * Find all four boundaries using binary search.
       *
       * This is O(log n) instead of scanning the complete dataset.
       */
      int isStartIndex = findFirstIndexAtOrAfter(marketData, isStart);

      int isEndIndex = findFirstIndexAtOrAfter(marketData, isEnd);

      int oosStartIndex = findFirstIndexAtOrAfter(marketData, oosStart);

      int oosEndIndex = findFirstIndexAtOrAfter(marketData, oosEnd);

      if (isStartIndex < 0
          || isEndIndex < 0
          || oosStartIndex < 0
          || oosEndIndex < 0
          || isStartIndex >= isEndIndex
          || oosStartIndex >= oosEndIndex) {

        isStart = isStart.plusMonths(stepMonths);
        windowNumber++;
        continue;
      }

      SMAMeanRevertingResult best = findBestParameters(marketData, isStartIndex, isEndIndex, hour);

      if (best != null) {

        /*
         * Run the selected IS parameters exactly once on OOS.
         */
        ZoneId zoneId = ZoneId.of("Europe/Madrid");

        int[] localHours = calculateLocalHours(marketData.candles(), zoneId);
        SMAMeanRevertingResult oos =
            analyzer.analyze(marketData, best.parameters(), oosStartIndex, oosEndIndex, localHours);

        System.out.println(
            "best "
                    +" "+best.parameters().smaLength() + " / "
                    +" " + best.parameters().distancePips() + " / "
                    +" " + best.parameters().exitAfterCandles() + " res: "
                + best.averagePips()
                + " OOS = "
                + oos.averagePips()
                + " | "
                + oos.netPips()
                + " | "
                + best.parameters());
        double equityBefore = equity;

        double oosPips = oos.netPips();

        cumulativePips += oosPips;

        equity += oosPips * pipValue;

        results.add(
            new WalkForwardResult(
                windowNumber,
                hour,
                isStart,
                isEnd,
                oosStart,
                oosEnd,
                best.parameters(),
                best,
                oos,
                equityBefore,
                equity,
                cumulativePips));
      }

      isStart = isStart.plusMonths(stepMonths);
      windowNumber++;
    }

    return results;
  }

  /**
   * Finds the best parameter configuration in the IS period.
   *
   * <p>This is the hottest part of the walk-forward analysis. Explicit nested loops are
   * intentionally used instead of streams to minimize allocation and lambda overhead.
   *
   * @param marketData market data
   * @param startIndex IS start index
   * @param endIndex IS end index
   * @param hour trading hour
   * @return best IS result
   */
  private SMAMeanRevertingResult findBestParameters(
      MarketData marketData, int startIndex, int endIndex, int hour) {

    int[] tradingHours = createTradingHours(hour);
    ZoneId zoneId = ZoneId.of("Europe/Madrid");

    int[] localHours = calculateLocalHours(marketData.candles(), zoneId);

    SMAMeanRevertingResult best = null;

    for (int sma : smaLengths) {

      for (double distance : distances) {

        for (double tp : tps) {

          for (double sl : sls) {

            for (int exit : exits) {

              for (double transactionCost : transactionCosts) {

                SMAMeanRevertingParameters parameters =
                    new SMAMeanRevertingParameters(
                        sma, distance, tp, sl, exit, transactionCost, tradingHours);

                SMAMeanRevertingResult result =
                    analyzer.analyze(marketData, parameters, startIndex, endIndex, localHours);

                if (!passesMinimumFilters(result)) {
                  continue;
                }

                if (best == null || compareResults(result, best) > 0) {
                  best = result;
                }
              }
            }
          }
        }
      }
    }

    return best;
  }

  /**
   * Compares two IS results.
   *
   * <p>The ranking gives priority to average pips, followed by profit factor and Sharpe ratio.
   *
   * @param left candidate result
   * @param right current best result
   * @return positive when left is better
   */
  private int compareResults(SMAMeanRevertingResult left, SMAMeanRevertingResult right) {

    int comparison = Double.compare(left.averagePips(), right.averagePips());

    if (comparison != 0) {
      return comparison;
    }

    comparison = Double.compare(left.profitFactor(), right.profitFactor());

    if (comparison != 0) {
      return comparison;
    }

    return Double.compare(left.sharpeRatio(), right.sharpeRatio());
  }

  /**
   * Applies minimum IS quality filters.
   *
   * @param result IS result
   * @return true when the result can be selected
   */
  private boolean passesMinimumFilters(SMAMeanRevertingResult result) {

    return result.operations() >= minimumOperations
        && result.averagePips() >= minimumAveragePips
        && result.profitFactor() >= minimumProfitFactor;
  }

  /**
   * Creates the trading-hour configuration.
   *
   * @param hour enabled trading hour
   * @return 24-element trading-hour array
   */
  private int[] createTradingHours(int hour) {

    int[] tradingHours = new int[24];

    Arrays.fill(tradingHours, 0);

    tradingHours[hour] = 1;

    return tradingHours;
  }

  /**
   * Finds the first candle whose timestamp is greater than or equal to the supplied date.
   *
   * @param marketData sorted market data
   * @param date target date
   * @return first matching index or -1
   */
  private int findFirstIndexAtOrAfter(MarketData marketData, LocalDateTime date) {

    int low = 0;
    int high = marketData.size() - 1;

    int result = -1;

    while (low <= high) {

      int middle = (low + high) >>> 1;

      Candle candle = marketData.get(middle);

      if (!candle.date().isBefore(date)) {
        result = middle;
        high = middle - 1;
      } else {
        low = middle + 1;
      }
    }

    return result;
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
