package com.alphapowertrading.simulator.core.broker;

import java.time.LocalDate;

public record Position(
        LocalDate entryDate,
        long entryPrice,
        int quantity,
        BuyType buyType
) {
}
