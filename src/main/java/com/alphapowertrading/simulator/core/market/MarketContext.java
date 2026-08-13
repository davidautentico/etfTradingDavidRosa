package com.alphapowertrading.simulator.core.market;

import java.time.LocalDate;

public record MarketContext(
        Candle candle,
        MarketData marketData,
        int index,
        double drawdown,
        double lowDrawdown,
        long peakClose,
        LocalDate peakDate
) {

    public boolean isFirstCandle() {
        return index == 0;
    }

    public boolean isLastCandle() {
        return index == marketData.size() - 1;
    }
}
