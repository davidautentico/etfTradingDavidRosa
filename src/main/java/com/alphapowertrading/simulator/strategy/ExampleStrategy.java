package com.alphapowertrading.simulator.strategy;

import com.alphapowertrading.simulator.core.broker.Broker;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.strategy.Strategy;
import org.springframework.stereotype.Component;

@Component
public class ExampleStrategy implements Strategy {

    @Override
    public void onCandle(
            Candle candle,
            MarketData marketData,
            int index,
            Broker broker
    ) {
        // TODO: implement trading strategy
        //
        // Prices are longs:
        // 381,70 in the CSV is represented as 38170.
    }
}
