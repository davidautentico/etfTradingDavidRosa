package com.alphapowertrading.simulator.cli;

import com.alphapowertrading.simulator.analytics.weekly.AnalyticsConfig;
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

  public SimulatorRunner(
      CsvLoader csvLoader,
      SimulatorProperties properties,
      Map<String, Strategy> strategies,
      JFreeChartGenerator chartGenerator,
      @Value("${simulator.commission-rate:0.0}") double commissionRate) {
    this.csvLoader = csvLoader;
    this.properties = properties;
    this.strategies = strategies;
    this.chartGenerator = chartGenerator;
    this.commissionRate = commissionRate;
  }

  private static Path writeTrades(AnalyticsConfig config, BacktestReport report) throws Exception {
    Path outputDirectory = Path.of(config.dataDirectory(), "trades");

    Files.createDirectories(outputDirectory);

    Path outputFile =
        outputDirectory.resolve(config.symbol() + "_trades_" + Instant.now().getNano() + ".csv");

    List<String> lines = new ArrayList<>();

    lines.add(
        "entryDate;exitDate;entryPrice;exitPrice;" + "quantity;profit%;closeReason;buyType;side");

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

    deletePreviousSymbolAnalysis(config.symbol(), outputDirectory);

    Files.write(outputFile, lines, StandardCharsets.UTF_8);

    return outputFile;
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

  private static void deletePreviousSymbolAnalysis(String symbol, Path outputDirectory)
      throws IOException {
    String prefix = symbol + "_trades";

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
                      "Unable to delete previous trade file: " + path.toAbsolutePath(), e);
                }
              });
    } catch (RuntimeException e) {
      if (e.getCause() instanceof IOException ioException) {
        throw ioException;
      }

      throw e;
    }
  }

  @Override
  public void run(String... args) throws Exception {
    Strategy strategy = strategies.get(properties.strategy());

    if (strategy == null) {
      throw new IllegalArgumentException(
          "Unknown strategy: "
              + properties.strategy()
              + ". Available strategies: "
              + strategies.keySet());
    }

    AnalyticsConfig config = AnalyticsConfig.load(args);

    Path file = Path.of(properties.dataDirectory(), properties.symbol() + ".csv");

    MarketData marketData = csvLoader.load(file);

    System.out.printf(
        "Loaded %d candles for %s (%s -> %s)%n",
        marketData.size(),
        properties.symbol(),
        marketData.get(0).date(),
        marketData.get(marketData.size() - 1).date());

    System.out.printf("Strategy: %s%n", properties.strategy());

    BacktestEngine engine =
        new BacktestEngine(
            properties.initialCapital(),
            properties.showTrades(),
            properties.showLossWeek(),
            properties.lossWeekThreshold(),
            properties.showMaxDd(),
            commissionRate);

    BacktestReport report = engine.run(marketData, strategy);

    printReportShort(report);
    writeTrades(config, report);

    Path chartDirectory = Path.of("output", "charts");

    Files.createDirectories(chartDirectory);

    /*chartGenerator.generate(
            properties.symbol(),
            properties.initialCapital(),
            marketData,
            report,
            chartDirectory
    );*/

    System.out.printf("Yearly charts generated in: %s%n", chartDirectory.toAbsolutePath());
  }

  private void printReport(BacktestReport report) {
    System.out.println();
    System.out.println("================ BACKTEST RESULT ================");

    System.out.printf("Final equity: %.2f%n", report.finalEquity());

    System.out.printf("Trades: %d%n", report.trades().size());

    System.out.printf("LONG: %d%n", report.longTrades());

    System.out.printf("SHORT: %d%n", report.shortTrades());

    System.out.printf("PnL: %.2f%n", report.totalProfit());

    System.out.printf("Factor: %.2f%n", report.finalEquity() / properties.initialCapital());

    System.out.printf("AvgDD: %.2f%%%n", report.averageDrawdown() * 100);

    System.out.printf("MaxDD: %.2f%%%n", report.maxDrawdown() * 100);

    System.out.printf("CAGR: %.2f%%%n", report.cagr() * 100);

    System.out.printf("Win%%: %.2f%%%n", report.winPercentage());

    System.out.printf("AvgWin: %.2f%%%n", report.averageWin());

    System.out.printf("AvgLose: %.2f%%%n", report.averageLose());
    System.out.printf("AvgPoints: %.2f%n", report.avgProfitInPips());

    System.out.printf("Profit Factor: %.2f%n", report.profitFactor());

    System.out.printf("Sharpe: %.2f%n", report.sharpeRatio());

    System.out.printf("Taxes: %.2f%n", report.totalTaxes());

    System.out.printf("Net Final Equity: %.2f%n", report.netFinalEquity());

    System.out.printf("Net Total Profit: %.2f%n", report.netTotalProfit());

    System.out.printf("Net CAGR: %.2f%%%n", report.netCagr() * 100);

    System.out.println("==================================================");
  }

    private void printReportShort(BacktestReport report) {
        System.out.println();
        System.out.println("================ BACKTEST RESULT ================");

        System.out.printf("Trades: %d | ", report.trades().size());

        System.out.printf("Win%%: %.2f%% | ", report.winPercentage());

        System.out.printf("AvgWin: %.2f%% | ", report.averageWin());

        System.out.printf("AvgLose: %.2f%% | ", report.averageLose());

        System.out.printf("AvgPoints: %.2f | ", report.avgProfitInPips());

        System.out.printf("PF: %.2f |", report.profitFactor());

        System.out.printf("Sharpe: %.2f |", report.sharpeRatio());

        System.out.printf("CAGR : %.2f |", report.cagr()*100.0);

        System.out.printf("AvgDD: %.2f | ", report.averageDrawdown()*100.0);

        System.out.printf("MaxDD: %.2f | ", report.maxDrawdown()*100.0);

      System.out.printf("Taxes: %.2f | ", report.totalTaxes());

      System.out.printf("Net Equity: %.2f%n", report.netFinalEquity());


        System.out.println("==================================================");
    }
}
