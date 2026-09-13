package com.alphapowertrading.portfolio.loader;

import com.alphapowertrading.portfolio.config.PortfolioProperties.Asset;
import com.alphapowertrading.portfolio.model.DailyReturnSeries;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DailyReturnsCsvLoader {

  public DailyReturnSeries load(Asset asset, Path dataDirectory) throws IOException {
    Path file = resolveFile(asset, dataDirectory);
    Map<LocalDate, Double> returns = new LinkedHashMap<>();

    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      int lineNumber = 0;
      int dateColumn = -1;
      int returnColumn = -1;

      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }

        String[] fields = line.split(";", -1);
        if (lineNumber == 1) {
          for (int i = 0; i < fields.length; i++) {
            String header = fields[i].trim().replace("\"", "");
            if (header.equalsIgnoreCase("date")) {
              dateColumn = i;
            } else if (header.equalsIgnoreCase("dailyReturn")) {
              returnColumn = i;
            }
          }
          if (dateColumn < 0 || returnColumn < 0) {
            throw new IOException("Missing date/dailyReturn columns in " + file);
          }
          continue;
        }

        if (fields.length <= Math.max(dateColumn, returnColumn)) {
          throw new IOException("Invalid CSV line " + lineNumber + " in " + file);
        }

        LocalDate date = LocalDate.parse(unquote(fields[dateColumn]));
        double dailyReturn = Double.parseDouble(unquote(fields[returnColumn]).replace(',', '.'));
        returns.put(date, dailyReturn);
      }
    }

    if (returns.isEmpty()) {
      throw new IOException("No daily returns found in " + file);
    }

    return new DailyReturnSeries(asset.symbol(), asset.strategy(), Map.copyOf(returns));
  }

  private Path resolveFile(Asset asset, Path dataDirectory) throws IOException {
    if (asset.dailyReturnsFile() != null && !asset.dailyReturnsFile().isBlank()) {
      Path file = dataDirectory.resolve(asset.dailyReturnsFile()).normalize();
      if (!Files.isRegularFile(file)) {
        throw new IOException("Daily returns file not found: " + file.toAbsolutePath());
      }
      return file;
    }

    String prefix = asset.symbol() + "_" + asset.strategy() + "_daily_returns";
    try (var files = Files.list(dataDirectory)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().startsWith(prefix))
          .sorted()
          .findFirst()
          .orElseThrow(
              () -> new IOException(
                  "No daily returns file found for " + asset.symbol() + " / " + asset.strategy()
                      + " under " + dataDirectory.toAbsolutePath()));
    }
  }

  private static String unquote(String value) {
    String result = value.trim();
    if (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
      return result.substring(1, result.length() - 1);
    }
    return result;
  }
}
