package com.alphapowertrading.correlation.model;

import java.time.LocalDate;
import java.util.Map;

public record StrategyReturns(String name, Map<LocalDate, Double> dailyReturns, int tradeCount) {}
