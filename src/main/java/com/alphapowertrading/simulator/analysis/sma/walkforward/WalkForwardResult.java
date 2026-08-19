package com.alphapowertrading.simulator.analysis.sma.walkforward;

import com.alphapowertrading.simulator.analysis.sma.SMAMeanRevertingParameters;
import com.alphapowertrading.simulator.analysis.sma.SMAMeanRevertingResult;
import java.time.LocalDateTime;

/** Stores the result of one walk-forward window. */
public record WalkForwardResult(
    int windowNumber,
    int hour,
    LocalDateTime isStart,
    LocalDateTime isEnd,
    LocalDateTime oosStart,
    LocalDateTime oosEnd,
    SMAMeanRevertingParameters parameters,
    SMAMeanRevertingResult isResult,
    SMAMeanRevertingResult oosResult,
    double equityBefore,
    double equityAfter,
    double cumulativePips) {}
