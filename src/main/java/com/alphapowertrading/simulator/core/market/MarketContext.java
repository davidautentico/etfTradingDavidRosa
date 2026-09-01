package com.alphapowertrading.simulator.core.market;

import java.time.LocalDate;

public record MarketContext(
    Candle candle,
    MarketData marketData,
    int index,
    double drawdown,
    double lowDrawdown,
    long peakClose,
    LocalDate peakDate,
    int simulationStartIndex,
    int simulationEndIndex) {

  public boolean isFirstCandle() {
    return index == simulationStartIndex;
  }

  public boolean isLastCandle() {
    return index == simulationEndIndex;
  }
}
