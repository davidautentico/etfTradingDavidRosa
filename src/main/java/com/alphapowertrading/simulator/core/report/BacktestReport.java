package com.alphapowertrading.simulator.core.report;

import com.alphapowertrading.simulator.core.broker.Trade;

import java.util.List;

public record BacktestReport(
        double cash,
        double finalEquity,
        List<Trade> trades
) {
    public double totalProfit() {
        return trades.stream()
                .mapToDouble(Trade::profit)
                .sum();
    }
}
