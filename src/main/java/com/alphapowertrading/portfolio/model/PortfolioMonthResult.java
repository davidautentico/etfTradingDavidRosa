package com.alphapowertrading.portfolio.model;

import java.time.YearMonth;
import java.util.Map;

public record PortfolioMonthResult(
    YearMonth month,
    Map<String, Double> sharpes,
    Map<String, Double> targetWeights,
    Map<String, Double> realWeightsBefore,
    Map<String, Double> realWeightsAfter,
    boolean rebalance,
    double turnover,
    double transactionCost) {}
