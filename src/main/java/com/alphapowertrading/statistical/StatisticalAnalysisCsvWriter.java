package com.alphapowertrading.statistical;

import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class StatisticalAnalysisCsvWriter {

  public void write(
      Path outputFile,
      Iterable<StatisticalResult> results)
      throws IOException {

    Path parent = outputFile.getParent();

    if (parent != null) {
      Files.createDirectories(parent);
    }

    try (BufferedWriter writer =
        Files.newBufferedWriter(
            outputFile,
            StandardCharsets.UTF_8)) {

      writer.write(
          "timestamp,sma_period,close_candles,"
              + "direction,entry_open,sma,deviation,"
              + "mae,mfe,open_close_difference");
      writer.newLine();

      for (StatisticalResult result : results) {
        writer.write(
            result.timestamp()
                .format(
                    java.time.format.DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss")));
        writer.write(",");
        writer.write(Integer.toString(
            result.smaPeriod()));
        writer.write(",");
        writer.write(Integer.toString(
            result.closeCandles()));
        writer.write(",");
        writer.write(result.direction().name());
        writer.write(",");
        writer.write(Integer.toString(
            result.entryOpen()));
        writer.write(",");
        writer.write(Integer.toString(
            result.sma()));
        writer.write(",");
        writer.write(Integer.toString(
            result.deviation()));
        writer.write(",");
        writer.write(Integer.toString(
            result.mae()));
        writer.write(",");
        writer.write(Integer.toString(
            result.mfe()));
        writer.write(",");
        writer.write(Integer.toString(
            result.openCloseDifference()));
        writer.newLine();
      }
    }
  }
}
