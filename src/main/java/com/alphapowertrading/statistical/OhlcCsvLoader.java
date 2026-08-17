package com.alphapowertrading.statistical;

import com.alphapowertrading.statistical.model.Ohlc;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class OhlcCsvLoader {

  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public List<Ohlc> load(Path file) throws IOException {
    List<Ohlc> candles = new ArrayList<>();

    try (BufferedReader reader =
        Files.newBufferedReader(file, StandardCharsets.UTF_8)) {

      String line = reader.readLine();

      if (line == null) {
        return candles;
      }

      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }

        candles.add(parse(line));
      }
    }

    return candles;
  }

  private Ohlc parse(String line) {
    String[] fields = line.split(",");

    if (fields.length < 5) {
      throw new IllegalArgumentException(
          "Invalid OHLC line: " + line);
    }

    try {
      return new Ohlc(
          LocalDateTime.parse(
              fields[0].trim(),
              TIMESTAMP_FORMATTER),
          Integer.parseInt(fields[1].trim()),
          Integer.parseInt(fields[2].trim()),
          Integer.parseInt(fields[3].trim()),
          Integer.parseInt(fields[4].trim()));
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(
          "Invalid OHLC line: " + line,
          exception);
    }
  }
}
