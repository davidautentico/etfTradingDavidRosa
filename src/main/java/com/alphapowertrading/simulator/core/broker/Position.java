package com.alphapowertrading.simulator.core.broker;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Position(
        LocalDateTime entryDate,
        long entryPrice,
        int quantity,
        BuyType buyType,
        PositionSide side
) {
}
