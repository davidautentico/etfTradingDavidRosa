package com.alphapowertrading.portfolio.model;

import java.time.LocalDate;
import java.util.Map;

public record DailyReturnSeries(String symbol, String strategy, Map<LocalDate, Double> dailyReturns) {}
