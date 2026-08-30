package com.alphapowertrading.correlation;

import com.alphapowertrading.correlation.config.CorrelationProperties;
import com.alphapowertrading.correlation.loader.TradeCsvLoader;
import com.alphapowertrading.correlation.model.CorrelationResult;
import com.alphapowertrading.correlation.model.StrategyPair;
import com.alphapowertrading.correlation.model.StrategyReturns;
import com.alphapowertrading.correlation.model.TradeRecord;
import com.alphapowertrading.correlation.service.CorrelationCalculator;
import com.alphapowertrading.correlation.service.CorrelationCsvWriter;
import com.alphapowertrading.correlation.service.StrategyReturnBuilder;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CorrelationRunner implements CommandLineRunner {

  private final CorrelationProperties properties;
  private final TradeCsvLoader tradeCsvLoader;
  private final StrategyReturnBuilder returnBuilder;
  private final CorrelationCalculator correlationCalculator;
  private final CorrelationCsvWriter csvWriter;

  public CorrelationRunner(
      CorrelationProperties properties,
      TradeCsvLoader tradeCsvLoader,
      StrategyReturnBuilder returnBuilder,
      CorrelationCalculator correlationCalculator,
      CorrelationCsvWriter csvWriter) {
    this.properties = properties;
    this.tradeCsvLoader = tradeCsvLoader;
    this.returnBuilder = returnBuilder;
    this.correlationCalculator = correlationCalculator;
    this.csvWriter = csvWriter;
  }

  @Override
  public void run(String... args) throws Exception {
    if (properties.tradeFiles().size() < 2) {
      throw new IllegalArgumentException(
          "At least two strategies must be configured under correlation.trade-files");
    }

    List<StrategyReturns> strategies = new ArrayList<>();

    for (Map.Entry<String, String> entry : properties.tradeFiles().entrySet()) {
      Path file = Path.of(entry.getValue());
      List<TradeRecord> allTrades = tradeCsvLoader.load(file);
      List<TradeRecord> trades = filterTradesByDate(allTrades);
      StrategyReturns returns = returnBuilder.build(entry.getKey(), trades);
      strategies.add(returns);

      System.out.printf(
          "Loaded %-20s %6d trades | %s -> %s%n",
          entry.getKey(),
          trades.size(),
          trades.stream().map(TradeRecord::exitDate).min(Comparator.naturalOrder()).orElse(null),
          trades.stream().map(TradeRecord::exitDate).max(Comparator.naturalOrder()).orElse(null));
    }

    if (strategies.stream().anyMatch(strategy -> strategy.dailyReturns().isEmpty())) {
      throw new IllegalArgumentException(
          "The selected date range contains no trades for at least one strategy");
    }

    CorrelationResult result = correlationCalculator.calculate(strategies);
    Map<LocalDate, Map<String, Double>> dailyReturns = buildDailyReturnTable(strategies);

    Path outputDirectory = Path.of(properties.outputDirectory());
    csvWriter.write(outputDirectory, result, strategies, dailyReturns);

    printResult(result, outputDirectory);
  }

  private List<TradeRecord> filterTradesByDate(List<TradeRecord> trades) {
    LocalDate startDate = properties.startDate();
    LocalDate endDate = properties.endDate();

    return trades.stream()
        .filter(trade -> startDate == null || !trade.exitDate().toLocalDate().isBefore(startDate))
        .filter(trade -> endDate == null || !trade.exitDate().toLocalDate().isAfter(endDate))
        .toList();
  }

  private Map<LocalDate, Map<String, Double>> buildDailyReturnTable(
      List<StrategyReturns> strategies) {
    Map<LocalDate, Map<String, Double>> table = new LinkedHashMap<>();

    LocalDate commonStart =
        strategies.stream()
            .map(
                strategy ->
                    strategy.dailyReturns().keySet().stream()
                        .min(Comparator.naturalOrder())
                        .orElseThrow())
            .max(Comparator.naturalOrder())
            .orElseThrow();
    LocalDate commonEnd =
        strategies.stream()
            .map(
                strategy ->
                    strategy.dailyReturns().keySet().stream()
                        .max(Comparator.naturalOrder())
                        .orElseThrow())
            .min(Comparator.naturalOrder())
            .orElseThrow();

    for (StrategyReturns strategy : strategies) {
      for (Map.Entry<LocalDate, Double> entry : strategy.dailyReturns().entrySet()) {
        if (!entry.getKey().isBefore(commonStart) && !entry.getKey().isAfter(commonEnd)) {
          table.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashMap<>())
              .put(strategy.name(), entry.getValue());
        }
      }
    }

    for (Map<String, Double> dailyValues : table.values()) {
      for (StrategyReturns strategy : strategies) {
        dailyValues.putIfAbsent(strategy.name(), 0.0);
      }
    }

    return table.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .collect(
            LinkedHashMap::new,
            (map, entry) -> map.put(entry.getKey(), entry.getValue()),
            LinkedHashMap::putAll);
  }

  private void printResult(CorrelationResult result, Path outputDirectory) {
    System.out.println();
    System.out.println("================ CORRELATION ================");
    System.out.printf("Date range: %s -> %s%n",
        properties.startDate() == null ? "BEGINNING" : properties.startDate(),
        properties.endDate() == null ? "END" : properties.endDate());
    System.out.printf("Strategies: %d%n", result.strategyNames().size());
    System.out.println();

    for (StrategyPair pair : result.pairs()) {
      System.out.printf(
          "%s <-> %s: %.4f%n",
          pair.firstStrategy(), pair.secondStrategy(), pair.correlation());
    }

    System.out.println();
    System.out.println("Output: " + outputDirectory.toAbsolutePath());
    System.out.println("==============================================");
  }
}
