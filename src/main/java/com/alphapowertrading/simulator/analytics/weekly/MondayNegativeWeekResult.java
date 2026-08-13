package com.alphapowertrading.simulator.analytics.weekly;

import java.time.LocalDate;

public record MondayNegativeWeekResult(
        LocalDate weekStart,
        long mondayOpen,
        long mondayClose,
        double mondayReturn,

        LocalDate weekCloseDate,
        long weekClose,
        double weekReturn,

        double recoveryFromMondayClose,
        boolean recovered,

        double highReturn,
        double lowReturn,

        LocalDate highDate,
        LocalDate lowDate
) {
}
