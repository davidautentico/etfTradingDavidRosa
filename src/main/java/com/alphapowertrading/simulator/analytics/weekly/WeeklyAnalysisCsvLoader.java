package com.alphapowertrading.simulator.analytics.weekly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** CSV loader used only by the standalone weekly analysis module. */
public final class WeeklyAnalysisCsvLoader {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private WeeklyAnalysisCsvLoader() {}

  public static List<WeeklyCandle> load(Path file) throws IOException {
    List<String> lines = Files.readAllLines(file);

    if (lines.isEmpty()) {
      throw new IllegalArgumentException("CSV is empty: " + file);
    }

    List<String> header =
        splitCsvLine(lines.get(0)).stream()
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .toList();

    int dateIndex = find(header, "fecha", "date");
    int openIndex = find(header, "apertura", "open");
    int highIndex = find(header, "máximo", "maximo", "high");
    int lowIndex = find(header, "mínimo", "minimo", "low");
    int closeIndex = find(header, "último", "ultimo", "close");

    if (dateIndex < 0 || openIndex < 0 || highIndex < 0 || lowIndex < 0 || closeIndex < 0) {
      throw new IllegalArgumentException(
          "CSV must contain Fecha, Apertura, Máximo, Mínimo and Último/Close columns");
    }

    List<WeeklyCandle> candles = new ArrayList<>();

    for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
      String line = lines.get(lineNumber);
      if (line.isBlank()) {
        continue;
      }

      List<String> values = splitCsvLine(line);
      int requiredIndex =
          Math.max(
              Math.max(Math.max(dateIndex, openIndex), Math.max(highIndex, lowIndex)), closeIndex);

      if (values.size() <= requiredIndex) {
        continue;
      }

      try {
        LocalDate date = LocalDate.parse(clean(values.get(dateIndex)), DATE_FORMAT);

        candles.add(
            new WeeklyCandle(
                date,
                parseNumber(values.get(openIndex)),
                parseNumber(values.get(highIndex)),
                parseNumber(values.get(lowIndex)),
                parseNumber(values.get(closeIndex))));
      } catch (RuntimeException e) {
        System.err.printf("Skipping malformed CSV line %d: %s%n", lineNumber + 1, line);
      }
    }

    candles.sort(Comparator.comparing(WeeklyCandle::date));
    return List.copyOf(candles);
  }

  private static int find(List<String> header, String... names) {
    for (String name : names) {
      int index = header.indexOf(name);
      if (index >= 0) {
        return index;
      }
    }
    return -1;
  }

  private static double parseNumber(String value) {
    String number = clean(value).replace(" ", "");

    if (number.contains(",") && number.contains(".")) {
      number = number.replace(".", "").replace(",", ".");
    } else if (number.contains(",")) {
      number = number.replace(",", ".");
    }

    return Double.parseDouble(number);
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim().replace("\"", "");
  }

  /** Splits a CSV line while respecting quoted fields. */
  private static List<String> splitCsvLine(String line) {
    List<String> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);

      if (c == '"') {
        if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        } else {
          quoted = !quoted;
        }
      } else if (c == ',' && !quoted) {
        result.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }

    result.add(current.toString());
    return result;
  }
}
