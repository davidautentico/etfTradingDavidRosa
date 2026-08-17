package com.alphapowertrading.tickconverter.eurusd.model;

import java.nio.file.Path;
import java.time.LocalDate;

public record EurUsdTickFile(
        Path path,
        String symbol,
        Side side,
        LocalDate date,
        int hour
) {
    public enum Side {
        BID,
        ASK
    }
}
