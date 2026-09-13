package com.alphapowertrading.portfolio.service;

import com.alphapowertrading.portfolio.config.PortfolioProperties;
import com.alphapowertrading.portfolio.model.DailyReturnSeries;
import com.alphapowertrading.portfolio.model.PortfolioMonthResult;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PortfolioSimulator {

  private final SharpeCalculator sharpeCalculator;
  private final PortfolioWeightCalculator weightCalculator;

  public PortfolioSimulator(
      SharpeCalculator sharpeCalculator, PortfolioWeightCalculator weightCalculator) {
    this.sharpeCalculator = sharpeCalculator;
    this.weightCalculator = weightCalculator;
  }

  public Result simulate(
      PortfolioProperties properties, Map<String, DailyReturnSeries> seriesBySymbol) {
    List<LocalDate> dates = allDates(seriesBySymbol, properties.startDate(), properties.endDate());
    if (dates.isEmpty()) {
      throw new IllegalArgumentException("No dates available for the configured portfolio");
    }

    Map<String, Double> realWeights = new LinkedHashMap<>();
    Map<LocalDate, Double> portfolioReturns = new LinkedHashMap<>();
    List<PortfolioMonthResult> monthResults = new ArrayList<>();
    YearMonth previousMonth = null;

    for (LocalDate date : dates) {
      YearMonth month = YearMonth.from(date);
      double transactionCostForDay = 0.0;
      if (!month.equals(previousMonth)) {
        LocalDate trainingEnd = month.atDay(1).minusDays(1);
        LocalDate trainingStart = month.minusMonths(properties.trainingMonths()).atDay(1);

        Map<String, Double> sharpes = new LinkedHashMap<>();
        for (Map.Entry<String, DailyReturnSeries> entry : seriesBySymbol.entrySet()) {
          sharpes.put(
              entry.getKey(),
              sharpeCalculator.calculate(entry.getValue().dailyReturns(), trainingStart, trainingEnd));
        }

        Map<String, Double> targetWeights = weightCalculator.calculate(
            sharpes, properties.minimumWeight(), properties.weightPrecision());

        Map<String, Double> realBefore = realWeights.isEmpty()
            ? new LinkedHashMap<>(targetWeights)
            : new LinkedHashMap<>(realWeights);

        boolean rebalance = realWeights.isEmpty()
            || targetWeights.entrySet().stream()
                .anyMatch(entry -> Math.abs(entry.getValue() - realBefore.get(entry.getKey()))
                    >= properties.rebalanceThreshold());

        double turnover = 0.0;
        double transactionCost = 0.0;
        if (rebalance) {
          if (!realWeights.isEmpty()) {
            turnover = targetWeights.keySet().stream()
                .mapToDouble(symbol -> Math.abs(targetWeights.get(symbol) - realBefore.get(symbol)))
                .sum();
              double tradedCapital = turnover / 2.0;
            transactionCost = tradedCapital
                * (properties.buyCost()
                    + properties.sellCost()
                    + properties.spreadSlippage());
            transactionCostForDay = transactionCost;
          }
          realWeights = new LinkedHashMap<>(targetWeights);
        } else {
          realWeights = new LinkedHashMap<>(realBefore);
        }

        monthResults.add(new PortfolioMonthResult(
            month,
            Map.copyOf(sharpes),
            Map.copyOf(targetWeights),
            Map.copyOf(realBefore),
            Map.copyOf(realWeights),
            rebalance,
            turnover,
            transactionCost));
        previousMonth = month;
      }

      double grossDailyReturn = 0.0;
      for (Map.Entry<String, Double> entry : realWeights.entrySet()) {
        grossDailyReturn += entry.getValue()
            * seriesBySymbol.get(entry.getKey()).dailyReturns().getOrDefault(date, 0.0);
      }

      double dailyReturn = grossDailyReturn - transactionCostForDay;
      portfolioReturns.put(date, dailyReturn);

      realWeights = driftWeights(realWeights, date, seriesBySymbol, grossDailyReturn);
    }

    return new Result(portfolioReturns, monthResults);
  }

  private static Map<String, Double> driftWeights(
      Map<String, Double> weights,
      LocalDate date,
      Map<String, DailyReturnSeries> seriesBySymbol,
      double portfolioReturn) {
    Map<String, Double> drifted = new LinkedHashMap<>();
    double denominator = 1.0 + portfolioReturn;
    for (Map.Entry<String, Double> entry : weights.entrySet()) {
      double assetReturn = seriesBySymbol.get(entry.getKey()).dailyReturns().getOrDefault(date, 0.0);
      drifted.put(entry.getKey(), entry.getValue() * (1.0 + assetReturn) / denominator);
    }
    return drifted;
  }

  private static List<LocalDate> allDates(
      Map<String, DailyReturnSeries> seriesBySymbol, LocalDate startDate, LocalDate endDate) {
    return seriesBySymbol.values().stream()
        .flatMap(series -> series.dailyReturns().keySet().stream())
        .distinct()
        .filter(date -> startDate == null || !date.isBefore(startDate))
        .filter(date -> endDate == null || !date.isAfter(endDate))
        .sorted(Comparator.naturalOrder())
        .toList();
  }

  public record Result(
      Map<LocalDate, Double> portfolioReturns,
      List<PortfolioMonthResult> monthResults) {}
}
