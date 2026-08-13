package com.alphapowertrading.simulator.core.broker;

import java.time.LocalDate;

public record Trade(
        LocalDate entryDate,
        LocalDate exitDate,
        long entryPrice,
        long exitPrice,
        int quantity,
        double profit,
        String closeReason,
        BuyType buyType
) {
}