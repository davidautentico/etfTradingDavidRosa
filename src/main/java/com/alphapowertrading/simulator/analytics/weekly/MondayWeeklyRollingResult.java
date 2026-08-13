package com.alphapowertrading.simulator.analytics.weekly;

import java.time.LocalDate;

public record MondayWeeklyRollingResult(
        LocalDate weekStart,
        long mondayOpen,
        long weeklyHigh,
        long weeklyLow,
        double highReturn,
        double lowReturn,
        double closeReturn,
        LocalDate highDate,
        LocalDate lowDate,
        LocalDate closeDate,
        double averageHighLast4Weeks,
        double averageLowLast4Weeks,
        int weeksInAverage
) {
}
