package com.alphapowertrading.simulator.core.market;

import java.time.LocalDate;

public record Candle(
        LocalDate date,
        long open,
        long high,
        long low,
        long close
) {
}
