package com.alphapowertrading.walkforward.model;

import java.time.LocalDate;

public record WalkForwardMonthlyResult(
        int window,
        LocalDate isStart,
        LocalDate isEnd,
        LocalDate oosStart,
        LocalDate oosEnd,
        double tp,
        double tph,
        double sl,
        double initialEquity,
        double finalEquity,
        double returnPct,
        int trades,
        double winPercentage
) {
}
