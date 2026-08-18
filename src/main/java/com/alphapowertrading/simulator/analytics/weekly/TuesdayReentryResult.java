package com.alphapowertrading.simulator.analytics.weekly;

import java.time.LocalDate;

/**
 * Result for a week with a Monday and a Tuesday trading session.
 *
 * <p>The Tuesday re-entry is measured from Tuesday OPEN.
 */
public record TuesdayReentryResult(
    LocalDate weekStart,
    double mondayReturn,
    boolean mondayNegative,
    LocalDate tuesdayDate,
    long tuesdayOpen,
    double tuesdayToWeekCloseReturn,
    double tuesdayToWeeklyHighReturn,
    double tuesdayToWeeklyLowReturn,
    LocalDate weekCloseDate,
    long weekClose,
    LocalDate highDate,
    LocalDate lowDate) {}
