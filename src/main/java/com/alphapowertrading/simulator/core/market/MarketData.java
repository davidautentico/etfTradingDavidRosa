package com.alphapowertrading.simulator.core.market;

import java.util.List;

public class MarketData {

    private final List<Candle> candles;

    public MarketData(List<Candle> candles) {
        this.candles = List.copyOf(candles);
    }

    public Candle get(int index) {
        return candles.get(index);
    }

    public int size() {
        return candles.size();
    }

    public List<Candle> candles() {
        return candles;
    }
}
