package com.alphapowertrading.simulator.core.broker;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Trade(
        LocalDateTime entryDate,
        LocalDateTime exitDate,
        long entryPrice,
        long exitPrice,
        int quantity,
        double profit,
        String closeReason,
        BuyType buyType,
        PositionSide side
) {
}
