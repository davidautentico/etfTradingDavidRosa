package com.alphapowertrading.simulator.analytics.weekly;

import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Analyzes the correlation between the weekly High - Monday Open range of
 * consecutive weeks.
 *
 * <p>For week N:
 *
 * <pre>
 * X = (High[N] - MondayOpen[N]) / MondayOpen[N]
 * </pre>
 *
 * <p>For week N-1:
 *
 * <pre>
 * Y = (High[N-1] - MondayOpen[N-1]) / MondayOpen[N-1]
 * </pre>
 *
 * <p>The analyzer calculates Pearson and Spearman correlations between X and Y.
 */
public class MondayWeeklyCorrelationAnalyzer {

  public AnalysisResult analyze(MarketData marketData) {
    List<WeeklyData> weeks = buildWeeks(marketData);

    if (weeks.size() < 2) {
      return new AnalysisResult(
          weeks,
          List.of(),
          Double.NaN,
          Double.NaN);
    }

    List<CorrelationData> correlationData =
        buildCorrelationData(weeks);

    double pearson =
        calculatePearson(
            correlationData,
            CorrelationData::previousHighReturn,
            CorrelationData::currentHighReturn);

    double spearman =
        calculateSpearman(
            correlationData,
            CorrelationData::previousHighReturn,
            CorrelationData::currentHighReturn);

    printAnalysis(
        weeks,
        correlationData,
        pearson,
        spearman);

    return new AnalysisResult(
        weeks,
        correlationData,
        pearson,
        spearman);
  }

  private List<WeeklyData> buildWeeks(MarketData marketData) {
    List<WeeklyData> weeks = new ArrayList<>();

    if (marketData == null || marketData.size() == 0) {
      return weeks;
    }

    int index = 0;

    while (index < marketData.size()) {
      Candle first = marketData.get(index);

      if (first.date().getDayOfWeek() != DayOfWeek.MONDAY) {
        index++;
        continue;
      }

      int end = index;
      long high = first.high();
      long low = first.low();

      LocalDate highDate = LocalDate.from(first.date());
      LocalDate lowDate = LocalDate.from(first.date());

      while (end + 1 < marketData.size()
          && sameWeek(
              first.date().toLocalDate(),
              marketData.get(end + 1).date().toLocalDate())) {

        end++;

        Candle candle = marketData.get(end);

        if (candle.high() > high) {
          high = candle.high();
          highDate = LocalDate.from(candle.date());
        }

        if (candle.low() < low) {
          low = candle.low();
          lowDate = LocalDate.from(candle.date());
        }
      }

      Candle lastTradingDay = marketData.get(end);

      long mondayOpen = first.open();
      long weeklyClose = lastTradingDay.close();

      double highPointsFromMonday =
          (double) high - mondayOpen;

      double highReturn =
          returnPct(mondayOpen, high);

      double lowReturn =
          returnPct(mondayOpen, low);

      double closeReturn =
          returnPct(mondayOpen, weeklyClose);

      weeks.add(
          new WeeklyData(
              first.date().toLocalDate(),
              mondayOpen,
              high,
              low,
              weeklyClose,
              highPointsFromMonday,
              highReturn,
              lowReturn,
              closeReturn,
              highDate,
              lowDate,
              lastTradingDay.date().toLocalDate()));

      index = end + 1;
    }

    return weeks;
  }

  private List<CorrelationData> buildCorrelationData(
      List<WeeklyData> weeks) {

    List<CorrelationData> result = new ArrayList<>();

    for (int i = 1; i < weeks.size(); i++) {
      WeeklyData previous = weeks.get(i - 1);
      WeeklyData current = weeks.get(i);

      result.add(
          new CorrelationData(
              previous.date(),
              current.date(),
              previous.highReturn(),
              current.highReturn(),
              previous.highPointsFromMonday(),
              current.highPointsFromMonday()));
    }

    return result;
  }

  private double calculatePearson(
      List<CorrelationData> data,
      ValueExtractor xExtractor,
      ValueExtractor yExtractor) {

    int n = data.size();

    if (n < 2) {
      return Double.NaN;
    }

    double sumX = 0.0;
    double sumY = 0.0;

    for (CorrelationData item : data) {
      sumX += xExtractor.get(item);
      sumY += yExtractor.get(item);
    }

    double meanX = sumX / n;
    double meanY = sumY / n;

    double numerator = 0.0;
    double denominatorX = 0.0;
    double denominatorY = 0.0;

    for (CorrelationData item : data) {
      double x = xExtractor.get(item) - meanX;
      double y = yExtractor.get(item) - meanY;

      numerator += x * y;
      denominatorX += x * x;
      denominatorY += y * y;
    }

    if (denominatorX == 0.0 || denominatorY == 0.0) {
      return Double.NaN;
    }

    return numerator / Math.sqrt(denominatorX * denominatorY);
  }

  private double calculateSpearman(
      List<CorrelationData> data,
      ValueExtractor xExtractor,
      ValueExtractor yExtractor) {

    int n = data.size();

    if (n < 2) {
      return Double.NaN;
    }

    List<Double> xValues = new ArrayList<>();
    List<Double> yValues = new ArrayList<>();

    for (CorrelationData item : data) {
      xValues.add(xExtractor.get(item));
      yValues.add(yExtractor.get(item));
    }

    double[] xRanks = calculateRanks(xValues);
    double[] yRanks = calculateRanks(yValues);

    return calculatePearson(xRanks, yRanks);
  }

  private double calculatePearson(double[] x, double[] y) {
    if (x.length != y.length || x.length < 2) {
      return Double.NaN;
    }

    double meanX = 0.0;
    double meanY = 0.0;

    for (int i = 0; i < x.length; i++) {
      meanX += x[i];
      meanY += y[i];
    }

    meanX /= x.length;
    meanY /= y.length;

    double numerator = 0.0;
    double denominatorX = 0.0;
    double denominatorY = 0.0;

    for (int i = 0; i < x.length; i++) {
      double dx = x[i] - meanX;
      double dy = y[i] - meanY;

      numerator += dx * dy;
      denominatorX += dx * dx;
      denominatorY += dy * dy;
    }

    if (denominatorX == 0.0 || denominatorY == 0.0) {
      return Double.NaN;
    }

    return numerator / Math.sqrt(denominatorX * denominatorY);
  }

  private double[] calculateRanks(List<Double> values) {
    int n = values.size();

    List<Integer> indexes = new ArrayList<>();

    for (int i = 0; i < n; i++) {
      indexes.add(i);
    }

    indexes.sort(Comparator.comparingDouble(values::get));

    double[] ranks = new double[n];

    int i = 0;

    while (i < n) {
      int j = i;

      while (j + 1 < n
          && Double.compare(
                  values.get(indexes.get(j)),
                  values.get(indexes.get(j + 1)))
              == 0) {
        j++;
      }

      double rank = ((double) i + j) / 2.0 + 1.0;

      for (int k = i; k <= j; k++) {
        ranks[indexes.get(k)] = rank;
      }

      i = j + 1;
    }

    return ranks;
  }

  private double returnPct(long entry, long exit) {
    if (entry == 0) {
      return 0.0;
    }

    return (double) exit / entry - 1.0;
  }

  private boolean sameWeek(LocalDate first, LocalDate second) {
    WeekFields fields = WeekFields.ISO;

    return first.get(fields.weekBasedYear())
            == second.get(fields.weekBasedYear())
        && first.get(fields.weekOfWeekBasedYear())
            == second.get(fields.weekOfWeekBasedYear());
  }

  private void printAnalysis(
      List<WeeklyData> weeks,
      List<CorrelationData> correlationData,
      double pearson,
      double spearman) {

    System.out.println();
    System.out.println(
        "============================================================");
    System.out.println(
        " WEEKLY HIGH - MONDAY OPEN CORRELATION");
    System.out.println(
        "============================================================");

    System.out.println("Weeks analyzed       = " + weeks.size());
    System.out.println(
        "Correlation pairs    = " + correlationData.size());
    System.out.println();

    System.out.println("CURRENT WEEK X:");
    System.out.println(
        "(High[N] - MondayOpen[N]) / MondayOpen[N]");
    System.out.println();

    System.out.println("PREVIOUS WEEK Y:");
    System.out.println(
        "(High[N-1] - MondayOpen[N-1]) / MondayOpen[N-1]");
    System.out.println();

    System.out.printf(
        "Pearson correlation  = %.4f%n",
        pearson);
    System.out.printf(
        "Spearman correlation = %.4f%n",
        spearman);

    System.out.println();

    printInterpretation("Pearson", pearson);
    printInterpretation("Spearman", spearman);

    System.out.println();

    System.out.println(
        "------------------------------------------------------------");
    System.out.println("CONSECUTIVE WEEK DATA");
    System.out.println(
        "------------------------------------------------------------");

    System.out.printf(
        "%-12s %-12s %12s %12s %14s %14s%n",
        "PREVIOUS",
        "CURRENT",
        "PREV HIGH %",
        "CURR HIGH %",
        "PREV POINTS",
        "CURR POINTS");

    for (CorrelationData item : correlationData) {
      System.out.printf(
          "%-12s %-12s %11.2f%% %11.2f%% %14.2f %14.2f%n",
          item.previousWeek(),
          item.currentWeek(),
          item.previousHighReturn() * 100.0,
          item.currentHighReturn() * 100.0,
          item.previousHighPoints(),
          item.currentHighPoints());
    }

    System.out.println(
        "------------------------------------------------------------");
    System.out.println("ALL WEEKLY DATA");
    System.out.println(
        "------------------------------------------------------------");

    System.out.printf(
        "%-12s %12s %12s %14s %12s %12s %12s%n",
        "DATE",
        "MON.OPEN",
        "HIGH",
        "HIGH-OPEN",
        "HIGH %",
        "CLOSE %",
        "LOW %");

    for (WeeklyData week : weeks) {
      System.out.printf(
          "%-12s %12d %12d %14.2f %11.2f%% %11.2f%% %11.2f%%%n",
          week.date(),
          week.mondayOpen(),
          week.high(),
          week.highPointsFromMonday(),
          week.highReturn() * 100.0,
          week.closeReturn() * 100.0,
          week.lowReturn() * 100.0);
    }

    System.out.println(
        "============================================================");
    System.out.println();
  }

  private void printInterpretation(
      String name,
      double correlation) {

    if (Double.isNaN(correlation)) {
      System.out.printf(
          "%s: NaN -> insufficient data%n",
          name);
      return;
    }

    double absolute = Math.abs(correlation);

    String interpretation;

    if (absolute >= 0.80) {
      interpretation = "MUY FUERTE";
    } else if (absolute >= 0.60) {
      interpretation = "FUERTE";
    } else if (absolute >= 0.40) {
      interpretation = "MODERADA";
    } else if (absolute >= 0.20) {
      interpretation = "DÉBIL";
    } else {
      interpretation = "MUY DÉBIL / NULA";
    }

    System.out.printf(
        "%s: %.4f -> %s%n",
        name,
        correlation,
        interpretation);
  }

  @FunctionalInterface
  private interface ValueExtractor {

    double get(CorrelationData data);
  }

  public record WeeklyData(
      LocalDate date,
      long mondayOpen,
      long high,
      long low,
      long close,
      double highPointsFromMonday,
      double highReturn,
      double lowReturn,
      double closeReturn,
      LocalDate highDate,
      LocalDate lowDate,
      LocalDate closeDate) {}

  public record CorrelationData(
      LocalDate previousWeek,
      LocalDate currentWeek,
      double previousHighReturn,
      double currentHighReturn,
      double previousHighPoints,
      double currentHighPoints) {}

  public record AnalysisResult(
      List<WeeklyData> weeks,
      List<CorrelationData> correlationData,
      double pearsonHighVsPreviousWeek,
      double spearmanHighVsPreviousWeek) {}
}
