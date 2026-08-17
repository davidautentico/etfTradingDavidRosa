package com.alphapowertrading.tickchecker.loader;

import com.alphapowertrading.tickchecker.model.Side;
import com.alphapowertrading.tickchecker.model.Tick;
import com.alphapowertrading.tickchecker.model.TickFile;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TickLoader {

  private static final Pattern FILE_PATTERN = Pattern.compile(
      "EURUSD_(BID|ASK)_(\\d{4}-\\d{2}-\\d{2})_(\\d{2})\\.log",
      Pattern.CASE_INSENSITIVE);

  public TickFile parse(Path path) {
    Matcher matcher =
        FILE_PATTERN.matcher(path.getFileName().toString());

    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Invalid EURUSD tick filename: " + path);
    }

    return new TickFile(
        path,
        Side.valueOf(matcher.group(1).toUpperCase()),
        LocalDate.parse(matcher.group(2)),
        Integer.parseInt(matcher.group(3)));
  }

  public long load(
      TickFile file,
      Consumer<Tick> consumer) throws IOException {

    long rows = 0;

    try (BufferedReader reader = Files.newBufferedReader(
        file.path(),
        StandardCharsets.UTF_8)) {

      String line;

      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }

        Tick tick = parseLine(line);

        if (tick != null) {
          consumer.accept(tick);
          rows++;
        }
      }
    }

    return rows;
  }

  private Tick parseLine(String line) {
    String[] fields = line.split(",");

    if (fields.length < 2) {
      return null;
    }

    try {
      long timestamp = Long.parseLong(fields[0].trim());
      int price = toIntegerPrice(fields[1].trim());

      return new Tick(
          Instant.ofEpochMilli(timestamp),
          price);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private int toIntegerPrice(String value) {
    return (int) Math.round(
        Double.parseDouble(value) * 100_000);
  }
}
