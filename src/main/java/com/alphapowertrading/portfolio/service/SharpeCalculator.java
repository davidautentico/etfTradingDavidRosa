package com.alphapowertrading.portfolio.service;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SharpeCalculator {

  private static final double ANNUALIZATION_FACTOR = Math.sqrt(252.0);

  public double calculate(List<Double> dailyReturns) {
    if (dailyReturns.size() < 2) {
      return 0.0;
    }

    double sum = 0.0;
    double sumSquared = 0.0;
    for (double value : dailyReturns) {
      sum += value;
      sumSquared += value * value;
    }

    double mean = sum / dailyReturns.size();
    double variance = sumSquared / dailyReturns.size() - mean * mean;
    if (variance <= 0.0) {
      return 0.0;
    }

    return mean / Math.sqrt(variance) * ANNUALIZATION_FACTOR;
  }

  public double calculate(java.util.Map<LocalDate, Double> returns, LocalDate start, LocalDate end) {
    List<Double> selected = returns.entrySet().stream()
        .filter(entry -> !entry.getKey().isBefore(start) && !entry.getKey().isAfter(end))
        .sorted(java.util.Map.Entry.comparingByKey())
        .map(java.util.Map.Entry::getValue)
        .toList();
    return calculate(selected);
  }
}
