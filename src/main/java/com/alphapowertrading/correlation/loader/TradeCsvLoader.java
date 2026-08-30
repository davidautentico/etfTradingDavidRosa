package com.alphapowertrading.correlation.loader;

import com.alphapowertrading.correlation.model.TradeRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TradeCsvLoader {

  public List<TradeRecord> load(Path file) throws IOException {
    List<String> lines = Files.readAllLines(file);

    if (lines.isEmpty()) {
      throw new IllegalArgumentException("CSV is empty: " + file);
    }

    List<String> header = splitCsvLine(lines.get(0)).stream().map(this::clean).toList();
    int entryDateIndex = find(header, "entryDate", "entrydate");
    int exitDateIndex = find(header, "exitDate", "exitdate");
    int profitIndex = find(header, "profit%", "profit", "pnl%", "pnl");

    if (entryDateIndex < 0 || exitDateIndex < 0 || profitIndex < 0) {
      throw new IllegalArgumentException(
          "CSV must contain entryDate, exitDate and profit% columns: " + file);
    }

    List<TradeRecord> trades = new ArrayList<>();

    for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
      String line = lines.get(lineNumber);
      if (line.isBlank()) {
        continue;
      }

      List<String> values = splitCsvLine(line);
      int requiredIndex = Math.max(entryDateIndex, Math.max(exitDateIndex, profitIndex));
      if (values.size() <= requiredIndex) {
        continue;
      }

      try {
        LocalDateTime entryDate = LocalDateTime.parse(clean(values.get(entryDateIndex)));
        LocalDateTime exitDate = LocalDateTime.parse(clean(values.get(exitDateIndex)));
        double profitPercentage = parseNumber(values.get(profitIndex)) / 100.0;

        trades.add(new TradeRecord(entryDate, exitDate, profitPercentage));
      } catch (RuntimeException e) {
        throw new IllegalArgumentException(
            "Malformed trade CSV line " + (lineNumber + 1) + " in " + file + ": " + line, e);
      }
    }

    return List.copyOf(trades);
  }

  private int find(List<String> header, String... names) {
    for (String name : names) {
      int index = header.indexOf(name);
      if (index >= 0) {
        return index;
      }
    }
    return -1;
  }

  private double parseNumber(String value) {
    String number = clean(value).replace(" ", "");

    if (number.contains(",") && number.contains(".")) {
      number = number.replace(".", "").replace(",", ".");
    } else if (number.contains(",")) {
      number = number.replace(",", ".");
    }

    return Double.parseDouble(number);
  }

  private String clean(String value) {
    return value == null ? "" : value.trim().replace("\"", "");
  }

  private List<String> splitCsvLine(String line) {
    List<String> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;

    for (int i = 0; i < line.length(); i++) {
      char character = line.charAt(i);

      if (character == '"') {
        if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        } else {
          quoted = !quoted;
        }
      } else if (character == ';' && !quoted) {
        result.add(current.toString());
        current.setLength(0);
      } else {
        current.append(character);
      }
    }

    result.add(current.toString());
    return result;
  }
}
