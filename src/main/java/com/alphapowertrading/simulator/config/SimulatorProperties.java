package com.alphapowertrading.simulator.config;

import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
    String symbol,
    List<String> symbols,
    int decimals,
    String dataDirectory,
    double initialCapital,
    String strategy,
    List<String> strategies,
    LocalDate startDate,
    LocalDate endDate,
    boolean showTrades,
    boolean showLossWeek,
    double lossWeekThreshold,
    boolean showMaxDd) {

  public SimulatorProperties {
    symbol = symbol == null || symbol.isBlank() ? "3QQQ" : symbol;
    symbols = normalize(symbols);
    if (symbols.isEmpty()) {
      symbols = List.of(symbol);
    }

    dataDirectory =
        dataDirectory == null || dataDirectory.isBlank() ? "data" : dataDirectory;
    decimals = decimals == 0 ? 2 : decimals;
    if (initialCapital <= 0) {
      initialCapital = 100_000.0;
    }

    strategy = strategy == null || strategy.isBlank() ? "oppwGapRecovery" : strategy;
    strategies = normalize(strategies);
    if (strategies.isEmpty()) {
      strategies = List.of(strategy);
    }

    if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
      throw new IllegalArgumentException(
          "Start date cannot be after end date: " + startDate + " > " + endDate);
    }
  }

  private static List<String> normalize(List<String> values) {
    if (values == null) {
      return List.of();
    }

    return values.stream()
        .flatMap(value -> List.of(value.split(",")).stream())
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .toList();
  }
}
