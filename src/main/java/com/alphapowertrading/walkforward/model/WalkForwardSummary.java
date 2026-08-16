package com.alphapowertrading.walkforward.model;

public record WalkForwardSummary(
        double initialEquity,
        double finalEquity,
        double cagr,
        double sharpe,
        double volatility,
        double maxDrawdown,
        double calmar,
        int months,
        int trades
) {
}
