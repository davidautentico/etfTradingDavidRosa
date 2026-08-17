package com.alphapowertrading.statistical;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "statistical-analysis")
public record StatisticalAnalysisProperties(
    boolean enabled,
    String inputFile,
    List<Integer> smaPeriods,
    List<Integer> closeCandles,
    List<Integer> hours,
    Direction direction,
    String outputFile) {

  public enum Direction {
    LONG,
    SHORT
  }
}
