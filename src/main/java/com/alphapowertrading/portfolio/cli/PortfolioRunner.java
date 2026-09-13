package com.alphapowertrading.portfolio.cli;

import com.alphapowertrading.portfolio.config.PortfolioProperties;
import com.alphapowertrading.portfolio.loader.DailyReturnsCsvLoader;
import com.alphapowertrading.portfolio.model.DailyReturnSeries;
import com.alphapowertrading.portfolio.model.PortfolioMonthResult;
import com.alphapowertrading.portfolio.service.PortfolioCsvWriter;
import com.alphapowertrading.portfolio.service.PortfolioSimulator;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class PortfolioRunner implements CommandLineRunner {

  private final PortfolioProperties properties;
  private final DailyReturnsCsvLoader loader;
  private final PortfolioSimulator simulator;
  private final PortfolioCsvWriter writer;

  public PortfolioRunner(
      PortfolioProperties properties,
      DailyReturnsCsvLoader loader,
      PortfolioSimulator simulator,
      PortfolioCsvWriter writer) {
    this.properties = properties;
    this.loader = loader;
    this.simulator = simulator;
    this.writer = writer;
  }

  @Override
  public void run(String... args) throws Exception {
    Path dataDirectory = Path.of(properties.dataDirectory());
    Map<String, DailyReturnSeries> series = new LinkedHashMap<>();

    for (PortfolioProperties.Asset asset : properties.assets()) {
      DailyReturnSeries loaded = loader.load(asset, dataDirectory);
      if (series.put(asset.symbol(), loaded) != null) {
        throw new IllegalArgumentException("Duplicate portfolio symbol: " + asset.symbol());
      }
      System.out.printf(
          "Loaded %-8s %-12s %6d daily returns%n",
          asset.symbol(), asset.strategy(), loaded.dailyReturns().size());
    }

    PortfolioSimulator.Result result = simulator.simulate(properties, series);
    writer.write(properties, result);

    System.out.println();
    System.out.println("================ PORTFOLIO ================");
    System.out.printf("Assets: %d%n", properties.assets().size());
    System.out.printf("Training window: %d months%n", properties.trainingMonths());
    System.out.printf("Rebalance threshold: %.1f%%%n", properties.rebalanceThreshold() * 100.0);
    System.out.printf("Monthly decisions: %d%n", result.monthResults().size());
    System.out.printf(
        "Rebalances: %d%n",
        result.monthResults().stream().filter(PortfolioMonthResult -> PortfolioMonthResult.rebalance()).count());

    if (!result.monthResults().isEmpty()) {
      PortfolioMonthResult latestMonth = result.monthResults().get(result.monthResults().size() - 1);
      System.out.println();
      System.out.printf("Latest month: %s%n", latestMonth.month());
      System.out.println("Target weights:");
      latestMonth.targetWeights().forEach(
          (symbol, weight) -> System.out.printf("  %-8s %.1f%%%n", symbol, weight * 100.0));
    }

    System.out.println("============================================");
    System.out.println("Output: " + Path.of(properties.outputDirectory()).toAbsolutePath());
  }
}
