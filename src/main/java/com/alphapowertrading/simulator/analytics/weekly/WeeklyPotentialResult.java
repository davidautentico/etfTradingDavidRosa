package com.alphapowertrading.simulator.analytics.weekly;

import java.time.DayOfWeek;
import java.time.LocalDate;

public record WeeklyPotentialResult(
    LocalDate weekStart,
    DayOfWeek entryDay,
    LocalDate entryDate,
    long entryOpen,
    LocalDate highDate,
    long weeklyHigh,
    double maxGain,
    LocalDate lowDate,
    long weeklyLow,
    double maxLoss,
    LocalDate exitDate,
    long exitClose,
    double closeReturn) {}
