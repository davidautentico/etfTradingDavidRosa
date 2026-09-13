package com.alphapowertrading.portfolio.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PortfolioWeightCalculator {

  public Map<String, Double> calculate(
      Map<String, Double> sharpes, double minimumWeight, double weightPrecision) {
    double positiveSharpeSum = sharpes.values().stream()
        .mapToDouble(value -> Math.max(value, 0.0))
        .sum();

    Map<String, Double> rawWeights = new LinkedHashMap<>();
    if (positiveSharpeSum == 0.0) {
      double equalWeight = 1.0 / sharpes.size();
      sharpes.keySet().forEach(symbol -> rawWeights.put(symbol, equalWeight));
    } else {
      double variableAllocation = 1.0 - minimumWeight * sharpes.size();
      if (variableAllocation < 0.0) {
        throw new IllegalArgumentException("Minimum weight is too large for the number of assets");
      }
      for (Map.Entry<String, Double> entry : sharpes.entrySet()) {
        double positiveSharpe = Math.max(entry.getValue(), 0.0);
        rawWeights.put(
            entry.getKey(), minimumWeight + variableAllocation * positiveSharpe / positiveSharpeSum);
      }
    }

    Map<String, Double> rounded = new LinkedHashMap<>();
    double sum = 0.0;
    for (Map.Entry<String, Double> entry : rawWeights.entrySet()) {
      double value = roundToPrecision(entry.getValue(), weightPrecision);
      rounded.put(entry.getKey(), value);
      sum += value;
    }

    double residual = roundToPrecision(1.0 - sum, weightPrecision);
    if (Math.abs(residual) > weightPrecision / 2.0) {
      String adjustmentSymbol = rounded.entrySet().stream()
          .max(Map.Entry.comparingByValue())
          .orElseThrow()
          .getKey();
      rounded.put(adjustmentSymbol, rounded.get(adjustmentSymbol) + residual);
    }

    return Map.copyOf(rounded);
  }

  private static double roundToPrecision(double value, double precision) {
    return Math.round(value / precision) * precision;
  }
}
