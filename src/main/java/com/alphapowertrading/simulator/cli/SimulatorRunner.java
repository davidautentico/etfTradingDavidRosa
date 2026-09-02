package com.alphapowertrading.simulator.cli;

import com.alphapowertrading.simulator.config.SimulatorProperties;
import com.alphapowertrading.simulator.core.broker.PositionSide;
import com.alphapowertrading.simulator.core.broker.Trade;
import com.alphapowertrading.simulator.core.chart.JFreeChartGenerator;
import com.alphapowertrading.simulator.core.engine.BacktestEngine;
import com.alphapowertrading.simulator.core.loader.CsvLoader;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.report.BacktestReport;
import com.alphapowertrading.simulator.core.strategy.Strategy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SimulatorRunner implements CommandLineRunner {

  private final CsvLoader csvLoader;
  private final SimulatorProperties properties;
  private final Map<String, Strategy> strategies;
  private final JFreeChartGenerator chartGenerator;
  private final double commissionRate;
  private final double spread;

  public SimulatorRunner(
      CsvLoader csvLoader,
      SimulatorProperties properties,
      Map<String, Strategy> strategies,
      JFreeChartGenerator chartGenerator,
      @Value("${simulator.commission-rate:0.0}") double commissionRate,
      @Value("${simulator.spread:0.0}") double spread) {
    this.csvLoader = csvLoader;
    this.properties = properties;
    this.strategies = strategies;
    this.chartGenerator = chartGenerator;
    this.commissionRate = commissionRate;
    this.spread = spread;
  }

  @Override
  public void run(String... args) throws Exception {
    List<BacktestResult> results = new ArrayList<>();

    for (String symbol : properties.symbols()) {
      MarketData marketData = loadMarketData(symbol);

      for (String strategyName : properties.strategies()) {
        results.add(runBacktest(symbol, strategyName, marketData));
      }
    }

    writeSummary(results);
    printSummary(results);
  }

  private MarketData loadMarketData(String symbol) throws IOException {
    Path file = Path.of(properties.dataDirectory(), symbol + ".csv");
    MarketData marketData = csvLoader.load(file);

    if (marketData.size() == 0) {
      throw new IllegalArgumentException("Market data is empty for symbol: " + symbol);
    }

    System.out.printf(
        "Loaded %d candles for %s (%s -> %s)%n",
        marketData.size(),
        symbol,
        marketData.get(0).date(),
        marketData.get(marketData.size() - 1).date());

    return marketData;
  }

  private BacktestResult runBacktest(
      String symbol, String strategyName, MarketData marketData) throws Exception {

    Strategy strategy = strategies.get(strategyName);

    if (strategy == null) {
      throw new IllegalArgumentException(
          "Unknown strategy: "
              + strategyName
              + ". Available strategies: "
              + strategies.keySet());
    }

    System.out.printf("%nRunning %s / %s", symbol, strategyName);
    if (properties.startDate() != null || properties.endDate() != null) {
      System.out.printf(
          " [%s -> %s]",
          properties.startDate() == null ? "start" : properties.startDate(),
          properties.endDate() == null ? "end" : properties.endDate());
    }
    System.out.println();

    BacktestEngine engine =
        new BacktestEngine(
            properties.initialCapital(),
            properties.showTrades(),
            properties.showLossWeek(),
            properties.lossWeekThreshold(),
            properties.showMaxDd(),
            commissionRate,
            spread,
            properties.startDate(),
            properties.endDate());

    BacktestReport report = engine.run(marketData, strategy);

    printReportShort(symbol, strategyName, report);
    writeTrades(symbol, strategyName, report);

    return new BacktestResult(symbol, strategyName, report);
  }

  private Path writeTrades(String symbol, String strategyName, BacktestReport report)
      throws Exception {
    Path outputDirectory = Path.of(properties.dataDirectory(), "trades");
    Files.createDirectories(outputDirectory);

    Path outputFile =
        outputDirectory.resolve(
            symbol + "_" + strategyName + "_trades_" + Instant.now().getNano() + ".csv");

    List<String> lines = new ArrayList<>();
    lines.add(
        "entryDate;exitDate;entryPrice;exitPrice;"
            + "quantity;profit%;closeReason;buyType;side");

    for (Trade trade : report.trades()) {
      lines.add(
          trade.entryDate()
              + ";"
              + trade.exitDate()
              + ";"
              + formatPrice(trade.entryPrice())
              + ";"
              + formatPrice(trade.exitPrice())
              + ";"
              + trade.quantity()
              + ";"
              + formatPct(pnlPercentage(trade))
              + ";"
              + trade.closeReason()
              + ";"
              + trade.buyType()
              + ";"
              + trade.side());
    }

    deletePreviousStrategyTrades(symbol, strategyName, outputDirectory);
    Files.write(outputFile, lines, StandardCharsets.UTF_8);

    return outputFile;
  }

  private void writeSummary(List<BacktestResult> results) throws IOException {
    Path outputDirectory = Path.of(properties.dataDirectory(), "trades");
    Files.createDirectories(outputDirectory);

    Path outputFile = outputDirectory.resolve("strategy_summary.csv");
    List<String> lines = new ArrayList<>();

    lines.add(
        "symbol;strategy;startDate;endDate;trades;win%;avgWin%;avgLose%;avgProfit%;"
            + "profitFactor;sharpe;cagr%;avgDD%;maxDD%;taxes;netEquity");

    for (BacktestResult result : results) {
      BacktestReport report = result.report();
      double avgProfit = calculateAverageProfit(report);

      lines.add(
          result.symbol()
              + ";"
              + result.strategy()
              + ";"
              + (properties.startDate() == null ? "" : properties.startDate())
              + ";"
              + (properties.endDate() == null ? "" : properties.endDate())
              + ";"
              + report.trades().size()
              + ";"
              + formatNumber(report.winPercentage())
              + ";"
              + formatNumber(report.averageWin())
              + ";"
              + formatNumber(report.averageLose())
              + ";"
              + formatNumber(avgProfit)
              + ";"
              + formatNumber(report.profitFactor())
              + ";"
              + formatNumber(report.sharpeRatio())
              + ";"
              + formatNumber(report.cagr() * 100.0)
              + ";"
              + formatNumber(report.averageDrawdown() * 100.0)
              + ";"
              + formatNumber(report.maxDrawdown() * 100.0)
              + ";"
              + formatNumber(report.totalTaxes())
              + ";"
              + formatNumber(report.netFinalEquity()));
    }

    Files.write(outputFile, lines, StandardCharsets.UTF_8);
    System.out.printf("%nCombined summary: %s%n", outputFile.toAbsolutePath());
  }

  private void printSummary(List<BacktestResult> results) {
    System.out.println();
    System.out.println(
        "====================== BACKTEST SUMMARY ======================");
    System.out.printf(
        "%-10s %-15s %8s %8s %9s %9s %9s %9s %9s%n",
        "Symbol", "Strategy", "Trades", "Win%", "AvgProfit", "PF", "Sharpe", "CAGR", "MAXDD");

    for (BacktestResult result : results) {
      BacktestReport report = result.report();

      System.out.printf(
          "%-10s %-15s %8d %8.2f %9.2f %9.2f %9.2f %9.2f %9.2f%n",
          result.symbol(),
          result.strategy(),
          report.trades().size(),
          report.winPercentage(),
          calculateAverageProfit(report),
          report.profitFactor(),
          report.sharpeRatio(),
          report.cagr() * 100.0,
              report.maxDrawdown()*100.0
      );
    }

    System.out.println(
        "==============================================================");
  }

  private void printReportShort(
      String symbol, String strategyName, BacktestReport report) {
    System.out.println();
    System.out.println("================ BACKTEST RESULT ================");

    System.out.printf("Symbol: %s | Strategy: %s%n", symbol, strategyName);
    System.out.printf("Trades: %d | ", report.trades().size());
    System.out.printf("Win%%: %.2f%% | ", report.winPercentage());
    System.out.printf("AvgProfit: %.2f%% | ", calculateAverageProfit(report));
    System.out.printf("AvgWin: %.2f%% (%d) | ", report.averageWin(), report.winningTrades());
    System.out.printf("AvgLose: %.2f%% (%d) | ", report.averageLose(), report.losingTrades());
    System.out.printf("AvgPoints: %.2f | ", report.avgProfitInPips());
    System.out.printf("PF: %.2f | ", report.profitFactor());
    System.out.printf("Sharpe: %.2f | ", report.sharpeRatio());
    System.out.printf("CAGR: %.2f%% | ", report.cagr() * 100.0);
    System.out.printf("AvgDD: %.2f%% | ", report.averageDrawdown() * 100.0);
    System.out.printf("MaxDD: %.2f%% | ", report.maxDrawdown() * 100.0);
    System.out.printf("Taxes: %.2f | ", report.totalTaxes());
    System.out.printf("Net Equity: %.2f%n", report.netFinalEquity());

    System.out.println("==================================================");
  }

  private double calculateAverageProfit(BacktestReport report) {
    if (report.losingTrades() == 0) {
      return report.averageWin();
    }

    if (report.winningTrades() == 0) {
      return report.averageLose();
    }

    return report.winningTrades()
        * report.averageWin()
        / (-report.losingTrades() * report.averageLose());
  }

  private static double pnlPercentage(Trade trade) {
    if (trade.side() == PositionSide.SHORT) {
      return ((double) trade.entryPrice() / trade.exitPrice() - 1);
    }

    return ((double) trade.exitPrice() / trade.entryPrice() - 1);
  }

  private static String formatPct(double value) {
    return String.format(new Locale("es", "ES"), "%.2f", value * 100);
  }

  private static String formatPrice(long value) {
    return String.format(Locale.US, "%.2f", value / 100.0);
  }

  private static String formatNumber(double value) {
    return String.format(Locale.US, "%.4f", value);
  }

  private static void deletePreviousStrategyTrades(
      String symbol, String strategyName, Path outputDirectory) throws IOException {
    String prefix = symbol + "_" + strategyName + "_trades";

    try (var files = Files.list(outputDirectory)) {
      files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().startsWith(prefix))
          .forEach(
              path -> {
                try {
                  Files.delete(path);
                } catch (IOException e) {
                  throw new RuntimeException(
                      "Unable to delete previous trade file: "
                          + path.toAbsolutePath(),
                      e);
                }
              });
    } catch (RuntimeException e) {
      if (e.getCause() instanceof IOException ioException) {
        throw ioException;
      }

      throw e;
    }
  }

  private record BacktestResult(String symbol, String strategy, BacktestReport report) {}
}
