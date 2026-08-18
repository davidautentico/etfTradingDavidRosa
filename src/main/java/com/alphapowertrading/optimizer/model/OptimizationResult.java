package com.alphapowertrading.optimizer.model;

public record OptimizationResult(
    double tp,
    double tph,
    double sl,
    double sharpe,
    double cagr,
    double maxDrawdown,
    double calmar,
    double finalEquity,
    int trades,
    double winRate) {}
