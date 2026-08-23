package com.alphapowertrading.optimizer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "optimizer")
public record OptimizationProperties(
        String symbol,
        String dataDirectory,
        double initialCapital,
        double commissionRate,
        boolean showProgress,
        Parameters tp,
        Parameters tph,
        Parameters sl,
        Parameters openGap,
        String outputFile,
        String sortBy) {

  public record Parameters(
          double min,
          double max,
          double step) {}
}