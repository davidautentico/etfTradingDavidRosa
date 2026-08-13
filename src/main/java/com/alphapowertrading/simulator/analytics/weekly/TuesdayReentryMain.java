package com.alphapowertrading.simulator.analytics.weekly;

import com.alphapowertrading.simulator.core.loader.CsvLoader;
import com.alphapowertrading.simulator.core.loader.CsvParser;
import com.alphapowertrading.simulator.core.market.MarketData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Independent analysis for a possible Tuesday re-entry.
 *
 * It compares:
 *
 *   1. Negative Monday
 *   2. Positive Monday
 *
 * and measures what happens after entering at Tuesday OPEN.
 */
public class TuesdayReentryMain {

    public static void main(String[] args) throws Exception {

        AnalyticsConfig config =
                AnalyticsConfig.load(args);

        Path csvFile =
                Path.of(
                        config.dataDirectory(),
                        config.symbol() + ".csv"
                );

        CsvLoader csvLoader =
                new CsvParser();

        MarketData marketData =
                csvLoader.load(csvFile);

        TuesdayReentryAnalyzer analyzer =
                new TuesdayReentryAnalyzer();

        List<TuesdayReentryResult> results =
                analyzer.analyze(marketData);

        printSummary(
                config.symbol(),
                results
        );

        printDetail(results);

        Path outputFile = writeCsv(config, results);

        System.out.println();
        System.out.println("Analysis CSV saved to:");
        System.out.println(outputFile.toAbsolutePath());
    }

    private static Path writeCsv(
            AnalyticsConfig config,
            List<TuesdayReentryResult> results
    ) throws Exception {

        Path outputDirectory = Path.of(
                config.dataDirectory(),
                "analysis"
        );

        Files.createDirectories(outputDirectory);

        Path outputFile = outputDirectory.resolve(
                config.symbol() + "_TUESDAY_REENTRY_ANALYSIS.csv"
        );

        List<String> lines = new java.util.ArrayList<>();

        lines.add(
                "week,monday_return_pct,monday_condition,"
                        + "tuesday_open,tuesday_to_week_close_pct,"
                        + "tuesday_to_week_high_pct,tuesday_to_week_low_pct,"
                        + "week_close_date,week_close,high_date,low_date"
        );

        for (TuesdayReentryResult result : results) {
            lines.add(
                    result.weekStart() + ","
                            + formatPct(result.mondayReturn()) + ","
                            + (result.mondayNegative() ? "NEGATIVE" : "POSITIVE") + ","
                            + formatPrice(result.tuesdayOpen()) + ","
                            + formatPct(result.tuesdayToWeekCloseReturn()) + ","
                            + formatPct(result.tuesdayToWeeklyHighReturn()) + ","
                            + formatPct(result.tuesdayToWeeklyLowReturn()) + ","
                            + result.weekCloseDate() + ","
                            + formatPrice(result.weekClose()) + ","
                            + result.highDate() + ","
                            + result.lowDate()
            );
        }

        Files.write(
                outputFile,
                lines,
                StandardCharsets.UTF_8
        );

        return outputFile;
    }

    private static String formatPct(double value) {
        return String.format(
                Locale.US,
                "%.4f",
                value * 100
        );
    }

    private static String formatPrice(long value) {
        return String.format(
                Locale.US,
                "%.2f",
                value / 100.0
        );
    }

    private static void printSummary(
            String symbol,
            List<TuesdayReentryResult> results
    ) {
        System.out.println();
        System.out.println(
                "=========================================================================="
        );
        System.out.println(
                "              TUESDAY RE-ENTRY ANALYSIS"
        );
        System.out.println(
                "=========================================================================="
        );

        System.out.println(
                "Symbol: " + symbol
        );

        System.out.println();

        printGroup(
                "NEGATIVE MONDAY",
                results.stream()
                        .filter(TuesdayReentryResult::mondayNegative)
                        .toList()
        );

        printGroup(
                "POSITIVE MONDAY",
                results.stream()
                        .filter(r -> !r.mondayNegative())
                        .toList()
        );

        System.out.println();
        System.out.println(
                "INTERPRETATION"
        );

        List<TuesdayReentryResult> negative =
                results.stream()
                        .filter(TuesdayReentryResult::mondayNegative)
                        .toList();

        List<TuesdayReentryResult> positive =
                results.stream()
                        .filter(r -> !r.mondayNegative())
                        .toList();

        if (!negative.isEmpty() && !positive.isEmpty()) {

            double negativeClose =
                    average(
                            negative,
                            TuesdayReentryResult::tuesdayToWeekCloseReturn
                    );

            double positiveClose =
                    average(
                            positive,
                            TuesdayReentryResult::tuesdayToWeekCloseReturn
                    );

            double negativeHigh =
                    average(
                            negative,
                            TuesdayReentryResult::tuesdayToWeeklyHighReturn
                    );

            double positiveHigh =
                    average(
                            positive,
                            TuesdayReentryResult::tuesdayToWeeklyHighReturn
                    );

            System.out.printf(
                    Locale.US,
                    "Tuesday -> week CLOSE: negative Monday %+7.2f%% vs positive Monday %+7.2f%%%n",
                    negativeClose * 100,
                    positiveClose * 100
            );

            System.out.printf(
                    Locale.US,
                    "Tuesday -> remaining HIGH: negative Monday %+7.2f%% vs positive Monday %+7.2f%%%n",
                    negativeHigh * 100,
                    positiveHigh * 100
            );

            double closeDifference =
                    negativeClose - positiveClose;

            double highDifference =
                    negativeHigh - positiveHigh;

            System.out.println();

            if (closeDifference > 0) {
                System.out.printf(
                        Locale.US,
                        "Negative Mondays show %.2f pp MORE Tuesday-to-close potential.%n",
                        closeDifference * 100
                );
            } else {
                System.out.printf(
                        Locale.US,
                        "Negative Mondays show %.2f pp LESS Tuesday-to-close potential.%n",
                        Math.abs(closeDifference) * 100
                );
            }

            if (highDifference > 0) {
                System.out.printf(
                        Locale.US,
                        "Negative Mondays show %.2f pp MORE remaining upside potential.%n",
                        highDifference * 100
                );
            } else {
                System.out.printf(
                        Locale.US,
                        "Negative Mondays show %.2f pp LESS remaining upside potential.%n",
                        Math.abs(highDifference) * 100
                );
            }
        }
    }

    private static void printGroup(
            String title,
            List<TuesdayReentryResult> results
    ) {
        System.out.println();
        System.out.println(
                "---------------- " + title + " ----------------"
        );

        if (results.isEmpty()) {
            System.out.println("No data.");
            return;
        }

        double mondayAverage =
                average(
                        results,
                        TuesdayReentryResult::mondayReturn
                );

        double closeAverage =
                average(
                        results,
                        TuesdayReentryResult::tuesdayToWeekCloseReturn
                );

        double highAverage =
                average(
                        results,
                        TuesdayReentryResult::tuesdayToWeeklyHighReturn
                );

        double lowAverage =
                average(
                        results,
                        TuesdayReentryResult::tuesdayToWeeklyLowReturn
                );

        long positiveClose =
                results.stream()
                        .filter(
                                r -> r.tuesdayToWeekCloseReturn() > 0
                        )
                        .count();

        long negativeClose =
                results.stream()
                        .filter(
                                r -> r.tuesdayToWeekCloseReturn() < 0
                        )
                        .count();

        System.out.printf(
                Locale.US,
                "Weeks:                         %d%n",
                results.size()
        );

        System.out.printf(
                Locale.US,
                "Average Monday return:        %+7.2f%%%n",
                mondayAverage * 100
        );

        System.out.printf(
                Locale.US,
                "Tuesday -> week CLOSE:        %+7.2f%%%n",
                closeAverage * 100
        );

        System.out.printf(
                Locale.US,
                "Tuesday -> remaining HIGH:    %+7.2f%%%n",
                highAverage * 100
        );

        System.out.printf(
                Locale.US,
                "Tuesday -> remaining LOW:     %+7.2f%%%n",
                lowAverage * 100
        );

        System.out.printf(
                Locale.US,
                "Tuesday re-entry positive:     %.2f%%%n",
                pct(positiveClose, results.size())
        );

        System.out.printf(
                Locale.US,
                "Tuesday re-entry negative:     %.2f%%%n",
                pct(negativeClose, results.size())
        );
    }

    private static void printDetail(
            List<TuesdayReentryResult> results
    ) {
        System.out.println();
        System.out.println(
                "DETAIL"
        );

        System.out.println(
                "Week         Mon       Tue->Close   Tue->HIGH   Tue->LOW   Condition"
        );

        System.out.println(
                "-----------------------------------------------------------------------"
        );

        for (TuesdayReentryResult result : results) {

            System.out.printf(
                    Locale.US,
                    "%s   %+8.2f%%   %+9.2f%%   %+9.2f%%   %+8.2f%%   %s%n",
                    result.weekStart(),
                    result.mondayReturn() * 100,
                    result.tuesdayToWeekCloseReturn() * 100,
                    result.tuesdayToWeeklyHighReturn() * 100,
                    result.tuesdayToWeeklyLowReturn() * 100,
                    result.mondayNegative()
                            ? "NEGATIVE"
                            : "POSITIVE"
            );
        }
    }

    private static double average(
            List<TuesdayReentryResult> results,
            java.util.function.ToDoubleFunction<TuesdayReentryResult> extractor
    ) {
        return results.stream()
                .mapToDouble(extractor)
                .average()
                .orElse(0);
    }

    private static double pct(
            long value,
            long total
    ) {
        return total == 0
                ? 0
                : value * 100.0 / total;
    }
}
