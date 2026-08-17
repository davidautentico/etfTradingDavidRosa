package com.alphapowertrading.simulator.core.market;

import java.time.LocalDateTime;

public record Candle(
        LocalDateTime date,
        long open,
        long high,
        long low,
        long close
) {
}
