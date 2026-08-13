package com.alphapowertrading.simulator.analytics.weekly;

import com.alphapowertrading.simulator.core.loader.CsvLoader;
import com.alphapowertrading.simulator.core.loader.CsvParser;
import com.alphapowertrading.simulator.core.market.MarketData;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class WeeklyAnalysisMain {

    public static void main(String[] args) throws Exception {

        AnalyticsConfig config =
                AnalyticsConfig.load(args);

        Path csvFile = Path.of(
                config.dataDirectory(),
                config.symbol() + ".csv"
        );

        System.out.println();
        System.out.println(
                "============================================================"
        );
        System.out.println(
                "       DAILY ENTRY / WEEKLY POTENTIAL ANALYSIS"
        );
        System.out.println(
                "============================================================"
        );
        System.out.println();
        System.out.println(
                "Symbol: " + config.symbol()
        );
        System.out.println(
                "CSV: " + csvFile.toAbsolutePath()
        );
        System.out.println();

        CsvLoader csvLoader =
                new CsvParser();

        MarketData marketData =
                csvLoader.load(csvFile);

        WeeklyPotentialAnalyzer analyzer =
                new WeeklyPotentialAnalyzer();

        List<WeeklyPotentialResult> results =
                analyzer.analyze(marketData);

        printSummary(results);
        printDetail(results);
    }

    private static void printSummary(
            List<WeeklyPotentialResult> results
    ) {
        System.out.println("SUMMARY");
        System.out.println();

        for (DayOfWeek day : orderedDays()) {

            List<WeeklyPotentialResult> dayResults =
                    results.stream()
                            .filter(
                                    result ->
                                            result.entryDay() == day
                            )
                            .toList();

            if (dayResults.isEmpty()) {
                System.out.printf(
                        "%-10s No data%n",
                        day
                );
                continue;
            }

            double averageHigh =
                    average(
                            dayResults,
                            Metric.HIGH
                    );

            double averageLow =
                    average(
                            dayResults,
                            Metric.LOW
                    );

            double averageClose =
                    average(
                            dayResults,
                            Metric.CLOSE
                    );

            double medianHigh =
                    median(
                            dayResults,
                            Metric.HIGH
                    );

            double medianLow =
                    median(
                            dayResults,
                            Metric.LOW
                    );

            double medianClose =
                    median(
                            dayResults,
                            Metric.CLOSE
                    );

            double highWinRate =
                    winRate(
                            dayResults,
                            Metric.HIGH
                    );

            double closeWinRate =
                    winRate(
                            dayResults,
                            Metric.CLOSE
                    );

            WeeklyPotentialResult bestHigh =
                    dayResults.stream()
                            .max(
                                    Comparator.comparingDouble(
                                            WeeklyPotentialResult::maxGain
                                    )
                            )
                            .orElseThrow();

            WeeklyPotentialResult worstLow =
                    dayResults.stream()
                            .min(
                                    Comparator.comparingDouble(
                                            WeeklyPotentialResult::maxLoss
                                    )
                            )
                            .orElseThrow();

            System.out.printf(
                    Locale.US,
                    "%-9s | Weeks: %4d | HIGH Avg: %+7.2f%% | LOW Avg: %+7.2f%% | CLOSE Avg: %+7.2f%%%n",
                    day,
                    dayResults.size(),
                    averageHigh * 100,
                    averageLow * 100,
                    averageClose * 100
            );

            System.out.printf(
                    Locale.US,
                    "          | HIGH Median: %+6.2f%% | LOW Median: %+6.2f%% | CLOSE Median: %+6.2f%%%n",
                    medianHigh * 100,
                    medianLow * 100,
                    medianClose * 100
            );

            System.out.printf(
                    Locale.US,
                    "          | HIGH Win%%: %6.2f%% | CLOSE Win%%: %6.2f%% | Best HIGH: %+6.2f%% | Worst LOW: %+6.2f%%%n",
                    highWinRate,
                    closeWinRate,
                    bestHigh.maxGain() * 100,
                    worstLow.maxLoss() * 100
            );

            System.out.println();
        }
    }

    private static void printDetail(
            List<WeeklyPotentialResult> results
    ) {
        System.out.println("DAILY DETAIL");
        System.out.println();

        System.out.println(
                "Day       Entry Date   Entry      High Exit     Gain       Low Exit      Loss       Close Exit     Return"
        );

        System.out.println(
                "-----------------------------------------------------------------------------------------------------------"
        );

        for (WeeklyPotentialResult result : results) {

            System.out.printf(
                    Locale.US,
                    "%-9s %s   %8.2f   %8.2f (%s) %+8.2f%%   %8.2f (%s) %+8.2f%%   %8.2f (%s) %+8.2f%%%n",
                    result.entryDay(),
                    result.entryDate(),
                    price(result.entryOpen()),

                    price(result.weeklyHigh()),
                    result.highDate(),
                    result.maxGain() * 100,

                    price(result.weeklyLow()),
                    result.lowDate(),
                    result.maxLoss() * 100,

                    price(result.exitClose()),
                    result.exitDate(),
                    result.closeReturn() * 100
            );
        }

        System.out.println();
    }

    private enum Metric {
        HIGH,
        LOW,
        CLOSE
    }

    private static double average(
            List<WeeklyPotentialResult> results,
            Metric metric
    ) {
        return results.stream()
                .mapToDouble(
                        result ->
                                metricValue(
                                        result,
                                        metric
                                )
                )
                .average()
                .orElse(0);
    }

    private static double median(
            List<WeeklyPotentialResult> results,
            Metric metric
    ) {
        List<Double> values =
                results.stream()
                        .map(
                                result ->
                                        metricValue(
                                                result,
                                                metric
                                        )
                        )
                        .sorted()
                        .toList();

        if (values.isEmpty()) {
            return 0;
        }

        int middle =
                values.size() / 2;

        if (values.size() % 2 == 0) {
            return (
                    values.get(middle - 1)
                            + values.get(middle)
            ) / 2.0;
        }

        return values.get(middle);
    }

    private static double winRate(
            List<WeeklyPotentialResult> results,
            Metric metric
    ) {
        long wins =
                results.stream()
                        .filter(
                                result ->
                                        metricValue(
                                                result,
                                                metric
                                        ) > 0
                        )
                        .count();

        return 100.0 * wins / results.size();
    }

    private static double metricValue(
            WeeklyPotentialResult result,
            Metric metric
    ) {
        return switch (metric) {
            case HIGH -> result.maxGain();
            case LOW -> result.maxLoss();
            case CLOSE -> result.closeReturn();
        };
    }

    private static DayOfWeek[] orderedDays() {
        return new DayOfWeek[]{
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
        };
    }

    private static double price(long value) {
        return value * 0.01;
    }
}
