package com.alphapowertrading.simulator.analysis.sma.walkforward;

import com.alphapowertrading.simulator.core.loader.MarketDataCsvLoader;
import com.alphapowertrading.simulator.core.market.MarketData;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * Standalone runner for rolling walk-forward analysis.
 *
 * <p>The default configuration is:
 *
 * <ul>
 *   <li>12 months in-sample
 *   <li>1 month out-of-sample
 *   <li>1 month step
 *   <li>one trading hour at a time
 * </ul>
 */
@SpringBootApplication(scanBasePackages = "com.alphapowertrading.simulator")
public class SMAMeanRevertingWalkForwardRunner {

  private static final String DEFAULT_FILE = "data/local/EURUSD_5m.csv";

  private static final String OUTPUT_DIRECTORY = "output/walkforward";

  private static final double INITIAL_EQUITY = 10_000.0;

  /*
   * One pip corresponds to one unit of hypothetical equity.
   *
   * Change this value if the equity curve should represent
   * a different monetary value per pip.
   */
  private static final double PIP_VALUE = 1.0;

  public static void main(String[] args) throws Exception {

    String file = args.length == 0 ? DEFAULT_FILE : args[0];

    System.out.println("========================================");
    System.out.println("SMA Mean Reverting Walk Forward");
    System.out.println("========================================");
    System.out.println("File: " + file);
    System.out.println();

    ApplicationContext context =
        SpringApplication.run(SMAMeanRevertingWalkForwardRunner.class, args);

    List<MarketDataCsvLoader> loaders =
        new ArrayList<>(context.getBeansOfType(MarketDataCsvLoader.class).values());

    MarketData marketData = loadMarketData(Path.of(file), loaders);

    System.out.println("Candles loaded: " + marketData.size());
    System.out.println();

    SMAMeanRevertingWalkForwardAnalyzer analyzer = new SMAMeanRevertingWalkForwardAnalyzer();

    List<WalkForwardResult> allResults = new ArrayList<>();

    long startTime = System.currentTimeMillis();

    /*
     * Run one independent walk-forward analysis for each trading hour.
     */
    for (int hour = 0; hour < 24; hour++) {

      System.out.println("Running hour " + hour + "...");

      List<WalkForwardResult> results =
          analyzer.analyzeHour(marketData, hour, INITIAL_EQUITY, PIP_VALUE);

      allResults.addAll(results);

      System.out.println("Hour " + hour + " completed: " + results.size() + " OOS windows");
    }

    long elapsed = System.currentTimeMillis() - startTime;

    Path outputDirectory = Path.of(OUTPUT_DIRECTORY);

    Files.createDirectories(outputDirectory);

    writeWindowsCsv(allResults, outputDirectory.resolve("walkforward_results.csv"));

    writeEquityCsv(allResults, outputDirectory.resolve("walkforward_equity.csv"));

    writeSummaryCsv(allResults, outputDirectory.resolve("walkforward_summary.csv"));

    System.out.println();

    System.out.println("========================================");
    System.out.println("Walk-forward completed");
    System.out.println("========================================");
    System.out.println("Windows: " + allResults.size());
    System.out.println("Elapsed: " + elapsed + " ms");
    System.out.println();

    printSummary(allResults);

    System.out.println();

    System.out.println("Output directory: " + outputDirectory.toAbsolutePath());
  }

  /**
   * Loads market data using the loader supporting the CSV header.
   *
   * @param file CSV file
   * @param loaders available loaders
   * @return market data
   * @throws IOException if the file cannot be read
   */
  private static MarketData loadMarketData(Path file, List<MarketDataCsvLoader> loaders)
      throws IOException {

    String header;

    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {

      header = reader.readLine();
    }

    if (header == null || header.isBlank()) {
      throw new IllegalArgumentException("CSV file is empty: " + file);
    }

    System.out.println("header: " + header);

    MarketDataCsvLoader selectedLoader = null;

    /*
     * Do not use streams here either.
     * The number of loaders is very small and an explicit loop
     * keeps the loader selection simple and predictable.
     */
    for (MarketDataCsvLoader loader : loaders) {

      if (loader.supports(header)) {
        selectedLoader = loader;
        break;
      }
    }

    if (selectedLoader == null) {
      throw new IllegalArgumentException("No CSV loader supports header: " + header);
    }

    System.out.println("CSV loader: " + selectedLoader.getClass().getSimpleName());

    return selectedLoader.load(file);
  }

  /**
   * Writes one row for every walk-forward window.
   *
   * @param results walk-forward results
   * @param file output file
   * @throws IOException if writing fails
   */
  private static void writeWindowsCsv(List<WalkForwardResult> results, Path file)
      throws IOException {

    try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {

      writer.write(
          "window,"
              + "hour,"
              + "isStart,"
              + "isEnd,"
              + "oosStart,"
              + "oosEnd,"
              + "smaLength,"
              + "distancePips,"
              + "tpPips,"
              + "slPips,"
              + "exitAfterCandles,"
              + "transactionCostPips,"
              + "isOperations,"
              + "isAveragePips,"
              + "isProfitFactor,"
              + "isSharpe,"
              + "oosOperations,"
              + "oosWinRate,"
              + "oosNetPips,"
              + "oosAveragePips,"
              + "oosProfitFactor,"
              + "oosSharpe,"
              + "oosMaxDrawdownPips,"
              + "equityBefore,"
              + "equityAfter,"
              + "cumulativePips");

      writer.newLine();

      for (WalkForwardResult result : results) {

        var parameters = result.parameters();
        var is = result.isResult();
        var oos = result.oosResult();

        writer.write(
            String.format(
                Locale.US,
                "%d,%d,%s,%s,%s,%s,"
                    + "%d,%.4f,%.4f,%.4f,%d,%.4f,"
                    + "%d,%.6f,%.6f,%.6f,"
                    + "%d,%.6f,%.6f,%.6f,%.6f,%.6f,"
                    + "%.6f,%.2f,%.2f,%.6f",
                result.windowNumber(),
                result.hour(),
                result.isStart(),
                result.isEnd(),
                result.oosStart(),
                result.oosEnd(),
                parameters.smaLength(),
                parameters.distancePips(),
                parameters.tpPips(),
                parameters.slPips(),
                parameters.exitAfterCandles(),
                parameters.transactionCostPips(),
                is.operations(),
                is.averagePips(),
                is.profitFactor(),
                is.sharpeRatio(),
                oos.operations(),
                oos.winRate(),
                oos.netPips(),
                oos.averagePips(),
                oos.profitFactor(),
                oos.sharpeRatio(),
                oos.maxDrawdownPips(),
                result.equityBefore(),
                result.equityAfter(),
                result.cumulativePips()));

        writer.newLine();
      }
    }
  }

  /**
   * Writes the concatenated OOS equity curve.
   *
   * @param results walk-forward results
   * @param file output file
   * @throws IOException if writing fails
   */
  private static void writeEquityCsv(List<WalkForwardResult> results, Path file)
      throws IOException {

    List<WalkForwardResult> sortedResults = new ArrayList<>(results);

    sortedResults.sort(
        Comparator.comparing(WalkForwardResult::oosStart)
            .thenComparingInt(WalkForwardResult::hour));

    try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {

      writer.write(
          "hour,"
              + "window,"
              + "oosStart,"
              + "oosEnd,"
              + "equityBefore,"
              + "equityAfter,"
              + "oosPips,"
              + "cumulativePips");

      writer.newLine();

      for (WalkForwardResult result : sortedResults) {

        writer.write(
            String.format(
                Locale.US,
                "%d,%d,%s,%s,%.2f,%.2f,%.4f,%.4f",
                result.hour(),
                result.windowNumber(),
                result.oosStart(),
                result.oosEnd(),
                result.equityBefore(),
                result.equityAfter(),
                result.oosResult().netPips(),
                result.cumulativePips()));

        writer.newLine();
      }
    }
  }

  /**
   * Writes a summary by trading hour.
   *
   * @param results walk-forward results
   * @param file output file
   * @throws IOException if writing fails
   */
  private static void writeSummaryCsv(List<WalkForwardResult> results, Path file)
      throws IOException {

    try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {

      writer.write(
          "hour,"
              + "windows,"
              + "positiveWindows,"
              + "positiveWindowPct,"
              + "totalOosPips,"
              + "averageOosPips,"
              + "averageProfitFactor,"
              + "averageSharpe");

      writer.newLine();

      /*
       * Iterate directly over the 24 hours.
       * No stream operations are executed inside this loop.
       */
      for (int hour = 0; hour < 24; hour++) {

        int windows = 0;
        int positiveWindows = 0;

        double totalPips = 0.0;
        double totalAveragePips = 0.0;

        double totalProfitFactor = 0.0;
        int validProfitFactorCount = 0;

        double totalSharpe = 0.0;
        int validSharpeCount = 0;

        for (WalkForwardResult result : results) {

          if (result.hour() != hour) {
            continue;
          }

          windows++;

          double oosPips = result.oosResult().netPips();

          if (oosPips > 0.0) {
            positiveWindows++;
          }

          totalPips += oosPips;

          totalAveragePips += result.oosResult().averagePips();

          double profitFactor = result.oosResult().profitFactor();

          if (Double.isFinite(profitFactor)) {
            totalProfitFactor += profitFactor;
            validProfitFactorCount++;
          }

          double sharpe = result.oosResult().sharpeRatio();

          if (Double.isFinite(sharpe)) {
            totalSharpe += sharpe;
            validSharpeCount++;
          }
        }

        if (windows == 0) {
          continue;
        }

        double positiveWindowPct = positiveWindows * 100.0 / windows;

        double averagePips = totalAveragePips / windows;

        double averageProfitFactor =
            validProfitFactorCount == 0 ? 0.0 : totalProfitFactor / validProfitFactorCount;

        double averageSharpe = validSharpeCount == 0 ? 0.0 : totalSharpe / validSharpeCount;

        writer.write(
            String.format(
                Locale.US,
                "%d,%d,%d,%.2f,%.4f,%.4f,%.4f,%.4f",
                hour,
                windows,
                positiveWindows,
                positiveWindowPct,
                totalPips,
                averagePips,
                averageProfitFactor,
                averageSharpe));

        writer.newLine();
      }
    }
  }

  /**
   * Prints the most relevant OOS statistics by hour.
   *
   * @param results walk-forward results
   */
  private static void printSummary(List<WalkForwardResult> results) {

    System.out.println("HOUR | WINDOWS | POSITIVE | OOS PIPS | AVG PIPS | AVG PF");

    System.out.println("----------------------------------------------------------");

    for (int hour = 0; hour < 24; hour++) {

      int windows = 0;
      int positiveWindows = 0;

      double totalPips = 0.0;
      double totalAveragePips = 0.0;

      double totalProfitFactor = 0.0;
      int validProfitFactorCount = 0;

      for (WalkForwardResult result : results) {

        if (result.hour() != hour) {
          continue;
        }

        windows++;

        double oosPips = result.oosResult().netPips();

        if (oosPips > 0.0) {
          positiveWindows++;
        }

        totalPips += oosPips;

        totalAveragePips += result.oosResult().averagePips();

        double profitFactor = result.oosResult().profitFactor();

        if (Double.isFinite(profitFactor)) {
          totalProfitFactor += profitFactor;
          validProfitFactorCount++;
        }
      }

      if (windows == 0) {
        continue;
      }

      double averagePips = totalAveragePips / windows;

      double averageProfitFactor =
          validProfitFactorCount == 0 ? 0.0 : totalProfitFactor / validProfitFactorCount;

      System.out.printf(
          Locale.US,
          "%4d | %7d | %8.1f%% | %8.2f | %8.3f | %7.3f%n",
          hour,
          windows,
          positiveWindows * 100.0 / windows,
          totalPips,
          averagePips,
          averageProfitFactor);
    }
  }
}
