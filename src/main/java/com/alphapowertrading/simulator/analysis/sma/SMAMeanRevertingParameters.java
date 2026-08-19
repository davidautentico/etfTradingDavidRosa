package com.alphapowertrading.simulator.analysis.sma;

import com.alphapowertrading.simulator.core.market.Candle;

import java.util.Arrays;

/**
 * Parameters used by the SMA mean-reverting strategy analyzer.
 */
public record SMAMeanRevertingParameters(
        int smaLength,
        double distancePips,
        double tpPips,
        double slPips,
        int exitAfterCandles,
        double transactionCostPips,
        int[] tradingHours) {

  public SMAMeanRevertingParameters {
    if (smaLength <= 0) {
      throw new IllegalArgumentException("SMA length must be greater than zero");
    }

    if (distancePips < 0) {
      throw new IllegalArgumentException("Distance cannot be negative");
    }

    if (tpPips <= 0) {
      throw new IllegalArgumentException("TP must be greater than zero");
    }

    if (slPips <= 0) {
      throw new IllegalArgumentException("SL must be greater than zero");
    }

    if (exitAfterCandles <= 0) {
      throw new IllegalArgumentException("Exit candles must be greater than zero");
    }

    if (transactionCostPips < 0) {
      throw new IllegalArgumentException("Transaction cost cannot be negative");
    }

    if (tradingHours == null || tradingHours.length == 0) {
      throw new IllegalArgumentException(
              "Trading hours must contain at least one hour");
    }

    for (int hour : tradingHours) {
      if (hour < 0 || hour > 23) {
        throw new IllegalArgumentException(
                "Trading hour must be between 0 and 23: " + hour);
      }
    }

    tradingHours = tradingHours.clone();
  }

  private boolean isTradingHour(
          Candle candle,
          int[] tradingHours) {

    int hour =
            candle.date().getHour();

    for (int tradingHour : tradingHours) {
      if (tradingHour == hour) {
        return true;
      }
    }

    return false;
  }

  @Override
  public int[] tradingHours() {
    return tradingHours.clone();
  }

  @Override
  public String toString() {
    return "SMA=" + smaLength
            + ", distance=" + distancePips
            + ", TP=" + tpPips
            + ", SL=" + slPips
            + ", exit=" + exitAfterCandles
            + ", cost=" + transactionCostPips
            + ", hours=" + Arrays.toString(tradingHours);
  }
}