package com.alphapowertrading.portfolio.config;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portfolio")
public record PortfolioProperties(
    List<Asset> assets,
    int trainingMonths,
    double minimumWeight,
    double rebalanceThreshold,
    double weightPrecision,
    double buyCost,
    double sellCost,
    double spreadSlippage,
    String dataDirectory,
    String outputDirectory,
    double initialCapital,
    LocalDate startDate,
    LocalDate endDate) {

  public PortfolioProperties {
    assets = assets == null ? defaultAssets() : List.copyOf(assets);
    trainingMonths = trainingMonths <= 0 ? 12 : trainingMonths;
    minimumWeight = minimumWeight <= 0 ? 0.05 : minimumWeight;
    rebalanceThreshold = rebalanceThreshold <= 0 ? 0.10 : rebalanceThreshold;
    weightPrecision = weightPrecision <= 0 ? 0.001 : weightPrecision;
    buyCost = buyCost < 0 ? 0.0005 : buyCost;
    sellCost = sellCost < 0 ? 0.0005 : sellCost;
    spreadSlippage = spreadSlippage < 0 ? 0.001 : spreadSlippage;
    dataDirectory = dataDirectory == null || dataDirectory.isBlank() ? "data/local" : dataDirectory;
    outputDirectory = outputDirectory == null || outputDirectory.isBlank()
        ? dataDirectory + "/trades"
        : outputDirectory;
    initialCapital = initialCapital <= 0 ? 100_000.0 : initialCapital;
  }

  private static List<Asset> defaultAssets() {
    List<Asset> defaults = new ArrayList<>();
    defaults.add(new Asset("LQQ", "fleuryv4", null));
    defaults.add(new Asset("EUNK", "byh", null));
    defaults.add(new Asset("CMOD", "byh", null));
    defaults.add(new Asset("8PSG", "byh", null));
    return defaults;
  }

  public record Asset(String symbol, String strategy, String dailyReturnsFile) {

    public Asset {
      if (symbol == null || symbol.isBlank()) {
        throw new IllegalArgumentException("Portfolio asset symbol cannot be blank");
      }
      if (strategy == null || strategy.isBlank()) {
        throw new IllegalArgumentException("Portfolio asset strategy cannot be blank: " + symbol);
      }
    }
  }
}
