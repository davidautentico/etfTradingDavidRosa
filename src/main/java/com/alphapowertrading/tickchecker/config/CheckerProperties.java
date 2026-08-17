package com.alphapowertrading.tickchecker.config;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tick-checker")
public record CheckerProperties(
    LocalDate date,
    LocalTime time,
    String inputDirectory,
    String oneMinuteFile) {

  public void validate() {
    if (date == null) {
      throw new IllegalArgumentException(
          "tick-checker.date is required");
    }

    if (time == null) {
      throw new IllegalArgumentException(
          "tick-checker.time is required");
    }

    if (inputDirectory == null || inputDirectory.isBlank()) {
      throw new IllegalArgumentException(
          "tick-checker.input-directory is required");
    }

    if (oneMinuteFile == null || oneMinuteFile.isBlank()) {
      throw new IllegalArgumentException(
          "tick-checker.one-minute-file is required");
    }
  }
}
