package com.alphapowertrading.walkforward.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

@ConfigurationProperties(prefix = "walkforward")
public record WalkForwardProperties(
    String symbol,
    String dataDirectory,
    String outputDirectory,
    LocalDate startDate,
    LocalDate endDate,
    double initialCapital,
    double commissionRate,
    int inSampleMonths,
    int outOfSampleMonths,
    int stepMonths,
    boolean showProgress,
    Parameters optimization) {

  public record Parameters(Range tp, Range tph, Range sl) {}

  public record Range(double min, double max, double step) {}
}
