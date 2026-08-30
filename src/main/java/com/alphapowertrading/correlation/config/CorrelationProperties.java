package com.alphapowertrading.correlation.config;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "correlation")
public record CorrelationProperties(
    double initialCapital,
    String outputDirectory,
    LocalDate startDate,
    LocalDate endDate,
    Map<String, String> tradeFiles) {

  public CorrelationProperties {
    if (initialCapital <= 0) {
      initialCapital = 100_000.0;
    }

    outputDirectory =
        outputDirectory == null || outputDirectory.isBlank()
            ? "data/correlation"
            : outputDirectory;

    if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
      throw new IllegalArgumentException("correlation.start-date must be on or before correlation.end-date");
    }

    tradeFiles = tradeFiles == null ? Map.of() : new LinkedHashMap<>(tradeFiles);
  }
}
