package com.alphapowertrading.simulator.core.broker;

import java.time.LocalDateTime;

public record Position(
        LocalDateTime entryDate,
        long entryPrice,
        int quantity,
        int index,
        BuyType buyType,
        PositionSide side
) {
}
