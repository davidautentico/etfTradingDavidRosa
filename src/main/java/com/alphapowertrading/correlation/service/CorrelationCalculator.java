package com.alphapowertrading.correlation.service;

import com.alphapowertrading.correlation.model.CorrelationResult;
import com.alphapowertrading.correlation.model.StrategyPair;
import com.alphapowertrading.correlation.model.StrategyReturns;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CorrelationCalculator {

  public CorrelationResult calculate(List<StrategyReturns> strategies) {
    List<String> names = strategies.stream().map(StrategyReturns::name).toList();
    Map<String, Map<String, Double>> matrix = new LinkedHashMap<>();
    List<StrategyPair> pairs = new ArrayList<>();

    for (int row = 0; row < strategies.size(); row++) {
      StrategyReturns first = strategies.get(row);
      Map<String, Double> rowValues = new LinkedHashMap<>();

      for (int column = 0; column < strategies.size(); column++) {
        StrategyReturns second = strategies.get(column);
        double correlation =
            row == column ? 1.0 : pearson(first.dailyReturns(), second.dailyReturns());

        rowValues.put(second.name(), correlation);

        if (column > row) {
          pairs.add(new StrategyPair(first.name(), second.name(), correlation));
        }
      }

      matrix.put(first.name(), rowValues);
    }

    return new CorrelationResult(names, matrix, pairs);
  }

  private double pearson(Map<LocalDate, Double> first, Map<LocalDate, Double> second) {
    LocalDate commonStart =
        first.keySet().stream().min(Comparator.naturalOrder()).orElse(null);
    LocalDate secondStart =
        second.keySet().stream().min(Comparator.naturalOrder()).orElse(null);
    LocalDate commonEnd =
        first.keySet().stream().max(Comparator.naturalOrder()).orElse(null);
    LocalDate secondEnd =
        second.keySet().stream().max(Comparator.naturalOrder()).orElse(null);

    if (commonStart == null || secondStart == null || commonEnd == null || secondEnd == null) {
      return Double.NaN;
    }

    LocalDate start = commonStart.isAfter(secondStart) ? commonStart : secondStart;
    LocalDate end = commonEnd.isBefore(secondEnd) ? commonEnd : secondEnd;

    if (start.isAfter(end)) {
      return Double.NaN;
    }

    List<LocalDate> dates = new ArrayList<>(first.keySet());
    dates.addAll(second.keySet());
    dates =
        dates.stream()
            .filter(date -> !date.isBefore(start) && !date.isAfter(end))
            .distinct()
            .sorted()
            .toList();

    if (dates.size() < 2) {
      return Double.NaN;
    }

    double firstMean =
        dates.stream().mapToDouble(date -> first.getOrDefault(date, 0.0)).average().orElse(0.0);
    double secondMean =
        dates.stream().mapToDouble(date -> second.getOrDefault(date, 0.0)).average().orElse(0.0);

    double covariance = 0.0;
    double firstVariance = 0.0;
    double secondVariance = 0.0;

    for (LocalDate date : dates) {
      double firstDeviation = first.getOrDefault(date, 0.0) - firstMean;
      double secondDeviation = second.getOrDefault(date, 0.0) - secondMean;

      covariance += firstDeviation * secondDeviation;
      firstVariance += firstDeviation * firstDeviation;
      secondVariance += secondDeviation * secondDeviation;
    }

    if (firstVariance == 0.0 || secondVariance == 0.0) {
      return Double.NaN;
    }

    return covariance / Math.sqrt(firstVariance * secondVariance);
  }
}
