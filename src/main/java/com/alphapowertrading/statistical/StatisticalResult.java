package com.alphapowertrading.statistical;

import com.alphapowertrading.statistical.StatisticalAnalysisProperties.Direction;

import java.time.LocalDateTime;

public record StatisticalResult(
    LocalDateTime timestamp,
    int smaPeriod,
    int closeCandles,
    Direction direction,
    int entryOpen,
    int sma,
    int deviation,
    int mae,
    int mfe,
    int openCloseDifference) {}
