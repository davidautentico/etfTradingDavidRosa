package com.alphapowertrading.simulator.analytics.weekly;

import com.alphapowertrading.simulator.core.loader.CsvLoader;
import com.alphapowertrading.simulator.core.loader.CsvParser;
import com.alphapowertrading.simulator.core.market.MarketData;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class MondayWeeklyRolling4Main {

    public static void main(String[] args) throws Exception {

        AnalyticsConfig config =
                AnalyticsConfig.load(args);

        Path csvFile =
                Path.of(
                        config.dataDirectory(),
                        config.symbol() + ".csv"
                );

        System.out.println();
        System.out.println(
                "=============================================================="
        );
        System.out.println(
                "      MONDAY ENTRY / WEEKLY HIGH-LOW-CLOSE / 4W AVG"
        );
        System.out.println(
                "=============================================================="
        );
        System.out.println();

        System.out.println(
                "Symbol: " + config.symbol()
        );

        System.out.println(
                "CSV: " + csvFile.toAbsolutePath()
        );

        CsvLoader csvLoader =
                new CsvParser();

        MarketData marketData =
                csvLoader.load(csvFile);

        MondayWeeklyRolling4Analyzer analyzer =
                new MondayWeeklyRolling4Analyzer();

        List<MondayWeeklyRollingResult> results =
                analyzer.analyze(marketData);

        printSummary(results);
        printDetail(results);
    }

    private static void printSummary(
            List<MondayWeeklyRollingResult> results
    ) {
        if (results.isEmpty()) {
            System.out.println(
                    "No Monday weeks found."
            );
            return;
        }

        double averageHigh =
                results.stream()
                        .mapToDouble(
                                MondayWeeklyRollingResult::highReturn
                        )
                        .average()
                        .orElse(0);

        double averageLow =
                results.stream()
                        .mapToDouble(
                                MondayWeeklyRollingResult::lowReturn
                        )
                        .average()
                        .orElse(0);

        double averageClose =
                results.stream()
                        .mapToDouble(
                                MondayWeeklyRollingResult::closeReturn
                        )
                        .average()
                        .orElse(0);

        System.out.printf(
                Locale.US,
                "All weeks average HIGH:  %+7.2f%%%n",
                averageHigh * 100
        );

        System.out.printf(
                Locale.US,
                "All weeks average LOW:   %+7.2f%%%n",
                averageLow * 100
        );

        System.out.printf(
                Locale.US,
                "All weeks average CLOSE: %+7.2f%%%n",
                averageClose * 100
        );

        System.out.println();
        System.out.println(
                "4-week averages: current week + previous 3 weeks."
        );
        System.out.println();
    }

    private static void printDetail(
            List<MondayWeeklyRollingResult> results
    ) {
        System.out.println(
                "WEEKLY DETAIL"
        );

        System.out.println(
                "Week         Open       HIGH       LOW      CLOSE      HIGH DATE    LOW DATE     CLOSE DATE   Avg HIGH 4W   Avg LOW 4W   N"
        );

        System.out.println(
                "--------------------------------------------------------------------------------------------------------------------------------"
        );

        for (MondayWeeklyRollingResult result : results) {

            System.out.printf(
                    Locale.US,
                    "%s   %8.2f   %+8.2f%%   %+8.2f%%   %+8.2f%%   %s   %s   %s   %+10.2f%%   %+10.2f%%   %d%n",
                    result.weekStart(),
                    price(result.mondayOpen()),
                    result.highReturn() * 100,
                    result.lowReturn() * 100,
                    result.closeReturn() * 100,
                    result.highDate(),
                    result.lowDate(),
                    result.closeDate(),
                    result.averageHighLast4Weeks() * 100,
                    result.averageLowLast4Weeks() * 100,
                    result.weeksInAverage()
            );
        }
    }

    private static double price(
            long value
    ) {
        return value * 0.01;
    }
}
