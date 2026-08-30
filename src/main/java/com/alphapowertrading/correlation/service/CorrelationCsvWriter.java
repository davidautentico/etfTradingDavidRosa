package com.alphapowertrading.correlation.service;

import com.alphapowertrading.correlation.model.CorrelationResult;
import com.alphapowertrading.correlation.model.StrategyPair;
import com.alphapowertrading.correlation.model.StrategyReturns;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CorrelationCsvWriter {

  public void write(
      Path outputDirectory,
      CorrelationResult result,
      List<StrategyReturns> strategies,
      Map<LocalDate, Map<String, Double>> dailyReturns)
      throws IOException {
    Files.createDirectories(outputDirectory);
    writeMatrix(outputDirectory.resolve("correlation_matrix.csv"), result);
    writePairs(outputDirectory.resolve("correlation_pairs.csv"), result.pairs());
    writeDailyReturns(outputDirectory.resolve("daily_returns.csv"), result.strategyNames(), dailyReturns);
    writeSummary(outputDirectory.resolve("strategy_summary.csv"), strategies);
  }

  private void writeMatrix(Path file, CorrelationResult result) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("strategy;" + String.join(";", result.strategyNames()));

    for (String strategy : result.strategyNames()) {
      List<String> values = new ArrayList<>();
      values.add(strategy);
      for (String other : result.strategyNames()) {
        values.add(format(result.matrix().get(strategy).get(other)));
      }
      lines.add(String.join(";", values));
    }

    Files.write(file, lines, StandardCharsets.UTF_8);
  }

  private void writePairs(Path file, List<StrategyPair> pairs) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("strategy1;strategy2;correlation");

    for (StrategyPair pair : pairs) {
      lines.add(
          pair.firstStrategy()
              + ";"
              + pair.secondStrategy()
              + ";"
              + format(pair.correlation()));
    }

    Files.write(file, lines, StandardCharsets.UTF_8);
  }

  private void writeDailyReturns(
      Path file, List<String> strategyNames, Map<LocalDate, Map<String, Double>> dailyReturns)
      throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("date;" + String.join(";", strategyNames));

    for (Map.Entry<LocalDate, Map<String, Double>> entry : dailyReturns.entrySet()) {
      List<String> values = new ArrayList<>();
      values.add(entry.getKey().toString());
      for (String strategy : strategyNames) {
        values.add(format(entry.getValue().getOrDefault(strategy, 0.0)));
      }
      lines.add(String.join(";", values));
    }

    Files.write(file, lines, StandardCharsets.UTF_8);
  }

  private void writeSummary(Path file, List<StrategyReturns> strategies) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("strategy;trades;daysWithReturns;totalCompoundReturn");

    for (StrategyReturns strategy : strategies) {
      double equityFactor =
          strategy.dailyReturns().values().stream().reduce(1.0, (factor, value) -> factor * (1.0 + value));
      lines.add(
          strategy.name()
              + ";"
              + strategy.tradeCount()
              + ";"
              + strategy.dailyReturns().size()
              + ";"
              + format(equityFactor - 1.0));
    }

    Files.write(file, lines, StandardCharsets.UTF_8);
  }

  private String format(double value) {
    if (Double.isNaN(value)) {
      return "NaN";
    }
    return String.format(Locale.US, "%.8f", value);
  }
}
