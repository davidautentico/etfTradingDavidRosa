package com.alphapowertrading.simulator.analysis.sma.walkforward;

import java.time.LocalDateTime;

/**
 * Represents one rolling walk-forward window.
 *
 * @param windowNumber sequential window number
 * @param isStart in-sample start
 * @param isEnd in-sample end
 * @param oosStart out-of-sample start
 * @param oosEnd out-of-sample end
 */
public record WalkForwardWindow(
    int windowNumber,
    LocalDateTime isStart,
    LocalDateTime isEnd,
    LocalDateTime oosStart,
    LocalDateTime oosEnd) {}
