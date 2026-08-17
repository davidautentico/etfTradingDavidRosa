package com.alphapowertrading.tickconverter.core.model;

import java.time.Instant;

public record BidAskCandle(
        Instant timestamp,
        int bidOpen,
        int bidHigh,
        int bidLow,
        int bidClose,
        int askOpen,
        int askHigh,
        int askLow,
        int askClose
) {
}