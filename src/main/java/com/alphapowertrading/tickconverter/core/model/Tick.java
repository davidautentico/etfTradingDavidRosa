package com.alphapowertrading.tickconverter.core.model;

import java.time.Instant;

public record Tick(
        Instant timestamp,
        int price,
        double volume
) {
}
