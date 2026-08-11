package com.alphapowertrading.simulator.cli;

import com.alphapowertrading.simulator.core.broker.Broker;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.strategy.Strategy;

public class OPPWStrategy implements Strategy {

    @Override
    public void initialize(Broker broker, MarketData marketData) {
        Strategy.super.initialize(broker, marketData);
    }

    @Override
    public void onCandle(Candle candle, MarketData marketData, int index, Broker broker) {

    }

    @Override
    public void finish(Candle lastCandle, MarketData marketData, Broker broker) {
        Strategy.super.finish(lastCandle, marketData, broker);
    }
}
