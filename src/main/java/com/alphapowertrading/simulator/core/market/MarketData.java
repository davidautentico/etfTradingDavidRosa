package com.alphapowertrading.simulator.core.market;

import java.util.ArrayList;
import java.util.List;

public class MarketData {

    private final List<Candle> candles;

    public MarketData(List<Candle> candles) {

        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException(
                    "Market data cannot be null or empty"
            );
        }

        this.candles = new ArrayList<>(candles);
    }

    public Candle get(int index) {
        return candles.get(index);
    }

    public int size() {
        return candles.size();
    }

    public List<Candle> candles() {
        return List.copyOf(candles);
    }
}