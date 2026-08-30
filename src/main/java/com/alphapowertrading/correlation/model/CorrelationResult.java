package com.alphapowertrading.correlation.model;

import java.util.List;
import java.util.Map;

public record CorrelationResult(
    List<String> strategyNames,
    Map<String, Map<String, Double>> matrix,
    List<StrategyPair> pairs) {}
