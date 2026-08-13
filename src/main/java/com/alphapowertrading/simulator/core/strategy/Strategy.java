package com.alphapowertrading.simulator.core.strategy;

import com.alphapowertrading.simulator.core.broker.Broker;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketContext;
import com.alphapowertrading.simulator.core.market.MarketData;

public interface Strategy {

    default void initialize(
            Broker broker,
            MarketData marketData
    ) {
    }

    void onCandle(
            MarketContext context,
            Broker broker
    );

    default void finish(
            Candle candle,
            MarketData marketData,
            Broker broker
    ) {
    }
}