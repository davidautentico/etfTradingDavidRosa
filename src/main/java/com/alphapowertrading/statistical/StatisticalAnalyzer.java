package com.alphapowertrading.statistical;

import com.alphapowertrading.statistical.StatisticalAnalysisProperties.Direction;
import com.alphapowertrading.statistical.model.Ohlc;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StatisticalAnalyzer {

  public List<StatisticalResult> analyze(
      List<Ohlc> candles,
      StatisticalAnalysisProperties properties) {

    validate(candles, properties);

    List<StatisticalResult> results = new ArrayList<>();

    for (int smaPeriod : properties.smaPeriods()) {
      for (int index = smaPeriod; index < candles.size(); index++) {
        Ohlc entryCandle = candles.get(index);

        if (!isHourAllowed(
            entryCandle,
            properties.hours())) {
          continue;
        }

        int sma = calculateSma(
            candles,
            index,
            smaPeriod);

        if (!matchesEntryCondition(
            entryCandle.open(),
            sma,
            properties.direction())) {
          continue;
        }

        for (int closeCandles : properties.closeCandles()) {
          int exitIndex = index + closeCandles;

          if (exitIndex >= candles.size()) {
            continue;
          }

          results.add(
              calculateResult(
                  candles,
                  index,
                  exitIndex,
                  smaPeriod,
                  closeCandles,
                  sma,
                  properties.direction()));
        }
      }
    }

    return results;
  }

  private StatisticalResult calculateResult(
      List<Ohlc> candles,
      int entryIndex,
      int exitIndex,
      int smaPeriod,
      int closeCandles,
      int sma,
      Direction direction) {

    Ohlc entryCandle = candles.get(entryIndex);
    Ohlc exitCandle = candles.get(exitIndex);

    int entryOpen = entryCandle.open();
    int highestHigh = Integer.MIN_VALUE;
    int lowestLow = Integer.MAX_VALUE;

    for (int index = entryIndex; index < exitIndex; index++) {
      Ohlc candle = candles.get(index);

      highestHigh = Math.max(
          highestHigh,
          candle.high());

      lowestLow = Math.min(
          lowestLow,
          candle.low());
    }

    int mae;
    int mfe;
    int openCloseDifference;

    if (direction == Direction.SHORT) {
      mfe = entryOpen - lowestLow;
      mae = highestHigh - entryOpen;
      openCloseDifference =
          entryOpen - exitCandle.close();
    } else {
      mfe = highestHigh - entryOpen;
      mae = entryOpen - lowestLow;
      openCloseDifference =
          exitCandle.close() - entryOpen;
    }

    return new StatisticalResult(
        entryCandle.timestamp(),
        smaPeriod,
        closeCandles,
        direction,
        entryOpen,
        sma,
        entryOpen - sma,
        mae,
        mfe,
        openCloseDifference);
  }

  private int calculateSma(
      List<Ohlc> candles,
      int index,
      int period) {

    long sum = 0;

    for (int i = index - period; i < index; i++) {
      sum += candles.get(i).open();
    }

    return Math.toIntExact(
        Math.round((double) sum / period));
  }

  private boolean matchesEntryCondition(
      int open,
      int sma,
      Direction direction) {

    if (direction == Direction.SHORT) {
      return open > sma;
    }

    return open < sma;
  }

  private boolean isHourAllowed(
      Ohlc candle,
      List<Integer> hours) {

    if (hours == null || hours.isEmpty()) {
      return true;
    }

    return hours.contains(
        candle.timestamp().getHour());
  }

  private void validate(
      List<Ohlc> candles,
      StatisticalAnalysisProperties properties) {

    if (candles == null || candles.isEmpty()) {
      throw new IllegalArgumentException(
          "OHLC data cannot be empty");
    }

    if (properties.smaPeriods() == null
        || properties.smaPeriods().isEmpty()) {
      throw new IllegalArgumentException(
          "At least one SMA period is required");
    }

    if (properties.closeCandles() == null
        || properties.closeCandles().isEmpty()) {
      throw new IllegalArgumentException(
          "At least one close candle value is required");
    }

    if (properties.direction() == null) {
      throw new IllegalArgumentException(
          "Direction is required");
    }
  }
}
