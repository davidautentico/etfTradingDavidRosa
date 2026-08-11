package com.alphapowertrading.simulator.core.engine;

import com.alphapowertrading.simulator.core.broker.Broker;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.report.BacktestReport;
import com.alphapowertrading.simulator.core.strategy.Strategy;

public class BacktestEngine {

    private final double initialCapital;

    public BacktestEngine(double initialCapital) {
        this.initialCapital = initialCapital;
    }

    public BacktestReport run(MarketData marketData, Strategy strategy) {
        if (marketData.size() == 0) {
            throw new IllegalArgumentException("Market data is empty");
        }

        Broker broker = new Broker(initialCapital);
        strategy.initialize(broker, marketData);

        for (int i = 0; i < marketData.size(); i++) {
            strategy.onCandle(
                    marketData.get(i),
                    marketData,
                    i,
                    broker
            );
        }

        var lastCandle = marketData.get(marketData.size() - 1);
        strategy.finish(lastCandle, marketData, broker);

        return broker.buildReport(lastCandle.close());
    }
}
