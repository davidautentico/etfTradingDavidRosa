package com.alphapowertrading.portfolio.service;

import com.alphapowertrading.portfolio.config.PortfolioProperties;
import com.alphapowertrading.portfolio.model.PortfolioMonthResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PortfolioCsvWriter {

  public void write(
      PortfolioProperties properties,
      PortfolioSimulator.Result result)
      throws IOException {
    Path outputDirectory = Path.of(properties.outputDirectory());
    Files.createDirectories(outputDirectory);

    writeDailyReturns(outputDirectory.resolve("portfolio_daily_returns.csv"), result);
    writeMonthly(outputDirectory.resolve("portfolio_monthly_weights.csv"), result);
  }

  private void writeDailyReturns(Path file, PortfolioSimulator.Result result) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("date;dailyReturn");
    for (var entry : result.portfolioReturns().entrySet()) {
      lines.add(entry.getKey() + ";" + format(entry.getValue(), 10));
    }
    Files.write(file, lines, StandardCharsets.UTF_8);
  }

  private void writeMonthly(Path file, PortfolioSimulator.Result result) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add(
        "month;symbol;sharpe;targetWeight;realWeightBefore;realWeightAfter;rebalance;turnover;transactionCost");
    for (PortfolioMonthResult month : result.monthResults()) {
      for (String symbol : month.targetWeights().keySet()) {
        lines.add(
            month.month()
                + ";"
                + symbol
                + ";"
                + format(month.sharpes().get(symbol), 6)
                + ";"
                + format(month.targetWeights().get(symbol) * 100.0, 1)
                + "%"
                + ";"
                + format(month.realWeightsBefore().get(symbol) * 100.0, 1)
                + "%"
                + ";"
                + format(month.realWeightsAfter().get(symbol) * 100.0, 1)
                + "%"
                + ";"
                + month.rebalance()
                + ";"
                + format(month.turnover() * 100.0, 3)
                + "%"
                + ";"
                + format(month.transactionCost() * 100.0, 4)
                + "%");
      }
    }
    Files.write(file, lines, StandardCharsets.UTF_8);
  }

  private static String format(double value, int decimals) {
    return String.format(Locale.US, "%1$." + decimals + "f", value);
  }
}
