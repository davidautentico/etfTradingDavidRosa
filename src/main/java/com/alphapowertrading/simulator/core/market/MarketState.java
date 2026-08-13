package com.alphapowertrading.simulator.core.market;

import java.time.LocalDate;

public class MarketState {

    private long peakClose;
    private LocalDate peakDate;

    public MarketState(long initialPrice, LocalDate initialDate) {
        if (initialPrice <= 0) {
            throw new IllegalArgumentException(
                    "Initial price must be greater than zero"
            );
        }

        this.peakClose = initialPrice;
        this.peakDate = initialDate;
    }

    public void update(long close, LocalDate date) {

        if (close > peakClose) {
            peakClose = close;
            peakDate = date;
        }
    }

    public long peakClose() {
        return peakClose;
    }

    public LocalDate peakDate() {
        return peakDate;
    }

    /**
     * Drawdown based on the current CLOSE.
     *
     * Example:
     * Peak = 100
     * Current = 90
     * DD = -10%
     */
    public double currentDrawdown(long currentClose) {

        if (peakClose <= 0) {
            return 0.0;
        }

        return (
                ((double) currentClose / peakClose) - 1.0
        ) * 100.0;
    }

    /**
     * Intraday drawdown based on the current LOW.
     */
    public double currentLowDrawdown(long currentLow) {

        if (peakClose <= 0) {
            return 0.0;
        }

        return (
                ((double) currentLow / peakClose) - 1.0
        ) * 100.0;
    }
}