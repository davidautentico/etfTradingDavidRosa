package com.alphapowertrading.tickchecker.checker;

import com.alphapowertrading.tickchecker.config.CheckerProperties;
import com.alphapowertrading.tickchecker.loader.TickLoader;
import com.alphapowertrading.tickchecker.model.Ohlc;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Component
public class BidAsk1mChecker {

  private static final DateTimeFormatter DISPLAY_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  private static final DateTimeFormatter CSV_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final TickLoader loader;

  public BidAsk1mChecker(TickLoader loader) {
    this.loader = loader;
  }

  public void check(CheckerProperties properties) throws IOException {
    properties.validate();

    LocalDateTime target =
        LocalDateTime.of(properties.date(), properties.time());

    Instant start =
        target.toInstant(ZoneOffset.UTC);

    Instant end =
        start.plusSeconds(60);

    Path inputDirectory =
        Path.of(properties.inputDirectory());

    Path oneMinuteFile =
        Path.of(properties.oneMinuteFile());

    System.out.println("==================================================");
    System.out.println("EURUSD 1M CHECKER");
    System.out.println("==================================================");
    System.out.println(
        "Target: " + target.format(CSV_FORMAT));
    System.out.println(
        "Input:  " + inputDirectory.toAbsolutePath());
    System.out.println(
        "1m file: " + oneMinuteFile.toAbsolutePath());
    System.out.println("==================================================");

    List<TickFile> files =
        findFiles(inputDirectory, properties);

    List<Tick> bidTicks =
        loadTicks(files, Side.BID, start, end);

    List<Tick> askTicks =
        loadTicks(files, Side.ASK, start, end);

    Ohlc actual =
        readActualCandle(oneMinuteFile, start);

    Ohlc expected =
        buildOhlc(start, bidTicks);

    printTicks("BID TICKS", bidTicks);
    printTicks("ASK TICKS", askTicks);

    printCandle(
        "OHLC FROM EURUSD_1m.csv",
        actual);

    printCandle(
        "OHLC CALCULATED FROM BID TICKS",
        expected);

    compare(actual, expected);
  }

  private List<TickFile> findFiles(
      Path directory,
      CheckerProperties properties) throws IOException {

    try (var stream = Files.list(directory)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path ->
              path.getFileName()
                  .toString()
                  .toLowerCase()
                  .endsWith(".log"))
          .map(loader::parse)
          .filter(file ->
              file.date().equals(properties.date()))
          .sorted(
              Comparator
                  .comparing(TickFile::side)
                  .thenComparingInt(TickFile::hour))
          .toList();
    }
  }

  private List<Tick> loadTicks(
      List<TickFile> files,
      Side side,
      Instant start,
      Instant end) throws IOException {

    List<Tick> result =
        new java.util.ArrayList<>();

    for (TickFile file : files) {
      if (file.side() != side) {
        continue;
      }

      loader.load(
          file,
          tick -> {
            if (!tick.timestamp().isBefore(start)
                && tick.timestamp().isBefore(end)) {
              result.add(tick);
            }
          });
    }

    result.sort(
        Comparator.comparing(Tick::timestamp));

    return result;
  }

  private Ohlc buildOhlc(
      Instant timestamp,
      List<Tick> ticks) {

    if (ticks.isEmpty()) {
      return null;
    }

    int open = ticks.get(0).price();
    int high = open;
    int low = open;
    int close = open;

    for (Tick tick : ticks) {
      high = Math.max(high, tick.price());
      low = Math.min(low, tick.price());
      close = tick.price();
    }

    return new Ohlc(
        timestamp,
        open,
        high,
        low,
        close);
  }

  private Ohlc readActualCandle(
      Path file,
      Instant target) throws IOException {

    try (BufferedReader reader =
        Files.newBufferedReader(
            file,
            StandardCharsets.UTF_8)) {

      String line = reader.readLine();

      if (line == null) {
        return null;
      }

      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }

        String[] fields =
            line.split(",");

        if (fields.length < 5) {
          continue;
        }

        Instant timestamp =
            LocalDateTime.parse(
                    fields[0].trim(),
                    CSV_FORMAT)
                .toInstant(ZoneOffset.UTC);

        if (!timestamp.equals(target)) {
          continue;
        }

        return new Ohlc(
            timestamp,
            Integer.parseInt(fields[1].trim()),
            Integer.parseInt(fields[2].trim()),
            Integer.parseInt(fields[3].trim()),
            Integer.parseInt(fields[4].trim()));
      }
    }

    return null;
  }

  private void printTicks(
      String title,
      List<Tick> ticks) {

    System.out.println();
    System.out.println(title);
    System.out.println("-----------------------------------------------");

    if (ticks.isEmpty()) {
      System.out.println("No ticks found.");
      return;
    }

    for (Tick tick : ticks) {
      System.out.printf(
          "%s  %d%n",
          DISPLAY_FORMAT
              .withZone(ZoneOffset.UTC)
              .format(tick.timestamp()),
          tick.price());
    }

    System.out.println(
        "Total ticks: " + ticks.size());
  }

  private void printCandle(
      String title,
      Ohlc candle) {

    System.out.println();
    System.out.println(title);
    System.out.println("-----------------------------------------------");

    if (candle == null) {
      System.out.println("Candle not found.");
      return;
    }

    System.out.printf(
        "Timestamp: %s%n",
        CSV_FORMAT
            .withZone(ZoneOffset.UTC)
            .format(candle.timestamp()));

    System.out.printf(
        "Open:      %d%n",
        candle.open());

    System.out.printf(
        "High:      %d%n",
        candle.high());

    System.out.printf(
        "Low:       %d%n",
        candle.low());

    System.out.printf(
        "Close:     %d%n",
        candle.close());
  }

  private void compare(
      Ohlc actual,
      Ohlc expected) {

    System.out.println();
    System.out.println("CHECK");
    System.out.println("-----------------------------------------------");

    if (actual == null) {
      System.out.println(
          "ERROR: 1-minute candle was not found.");
      return;
    }

    if (expected == null) {
      System.out.println(
          "ERROR: No BID ticks found for this minute.");
      return;
    }

    checkField(
        "OPEN",
        actual.open(),
        expected.open());

    checkField(
        "HIGH",
        actual.high(),
        expected.high());

    checkField(
        "LOW",
        actual.low(),
        expected.low());

    checkField(
        "CLOSE",
        actual.close(),
        expected.close());

    boolean passed =
        actual.open() == expected.open()
            && actual.high() == expected.high()
            && actual.low() == expected.low()
            && actual.close() == expected.close();

    System.out.println("-----------------------------------------------");
    System.out.println(
        passed ? "CHECK PASSED" : "CHECK FAILED");
    System.out.println("==================================================");
  }

  private void checkField(
      String field,
      int actual,
      int expected) {

    String status =
        actual == expected
            ? "OK"
            : "ERROR";

    System.out.printf(
        "%-6s: %s (file=%d, calculated=%d)%n",
        field,
        status,
        actual,
        expected);
  }
}
