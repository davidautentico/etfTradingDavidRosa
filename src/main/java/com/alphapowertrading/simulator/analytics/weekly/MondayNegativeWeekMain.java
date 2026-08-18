package com.alphapowertrading.simulator.analytics.weekly;

import com.alphapowertrading.simulator.core.loader.CsvLoader;
import com.alphapowertrading.simulator.core.loader.CsvParser;
import com.alphapowertrading.simulator.core.market.MarketData;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Standalone main for the "negative Monday" statistic.
 *
 * It loads the same CSV configured in application.yml and does not
 * start the simulator or Spring Boot.
 */
public class MondayNegativeWeekMain {

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

        MondayNegativeWeekAnalyzer analyzer =
                new MondayNegativeWeekAnalyzer();

        List<MondayNegativeWeekResult> results =
                analyzer.analyze(marketData);

        printSummary(
                config.symbol(),
                results
        );

        printDetail(results);
    }

    private static void printSummary(
            String symbol,
            List<MondayNegativeWeekResult> results
    ) {
        System.out.println();
        System.out.println(
                "=============================================================="
        );
        System.out.println(
                "             NEGATIVE MONDAY WEEK ANALYSIS"
        );
        System.out.println(
                "=============================================================="
        );

        System.out.println(
                "Symbol: " + symbol
        );

        System.out.println();

        if (results.isEmpty()) {
            System.out.println(
                    "No weeks with a negative Monday close."
            );
            return;
        }

        int total = results.size();

        long recovered =
                results.stream()
                        .filter(
                                MondayNegativeWeekResult::recovered
                        )
                        .count();

        long continuedLoss =
                total - recovered;

        long weekPositive =
                results.stream()
                        .filter(
                                r -> r.weekReturn() > 0
                        )
                        .count();

        long weekNegative =
                results.stream()
                        .filter(
                                r -> r.weekReturn() < 0
                        )
                        .count();

        double averageMonday =
                results.stream()
                        .mapToDouble(
                                MondayNegativeWeekResult::mondayReturn
                        )
                        .average()
                        .orElse(0);

        double averageWeek =
                results.stream()
                        .mapToDouble(
                                MondayNegativeWeekResult::weekReturn
                        )
                        .average()
                        .orElse(0);

        double averageRecovery =
                results.stream()
                        .mapToDouble(
                                MondayNegativeWeekResult::recoveryFromMondayClose
                        )
                        .average()
                        .orElse(0);

        double averageHigh =
                results.stream()
                        .mapToDouble(
                                MondayNegativeWeekResult::highReturn
                        )
                        .average()
                        .orElse(0);

        double averageLow =
                results.stream()
                        .mapToDouble(
                                MondayNegativeWeekResult::lowReturn
                        )
                        .average()
                        .orElse(0);

        System.out.printf(
                Locale.US,
                "Negative Mondays:              %d%n",
                total
        );

        System.out.printf(
                Locale.US,
                "Average Monday return:        %+7.2f%%%n",
                averageMonday * 100
        );

        System.out.printf(
                Locale.US,
                "Average week return:          %+7.2f%%%n",
                averageWeek * 100
        );

        System.out.printf(
                Locale.US,
                "Average recovery Mon->week:   %+7.2f%%%n",
                averageRecovery * 100
        );

        System.out.println();

        System.out.println(
                "AFTER A NEGATIVE MONDAY"
        );

        System.out.printf(
                Locale.US,
                "Remonta:                       %d / %d (%.2f%%)%n",
                recovered,
                total,
                pct(recovered, total)
        );

        System.out.printf(
                Locale.US,
                "Continua perdiendo:            %d / %d (%.2f%%)%n",
                continuedLoss,
                total,
                pct(continuedLoss, total)
        );

        System.out.println();

        System.out.println(
                "WEEK RESULT VS MONDAY OPEN"
        );

        System.out.printf(
                Locale.US,
                "Semana positiva:               %d / %d (%.2f%%)%n",
                weekPositive,
                total,
                pct(weekPositive, total)
        );

        System.out.printf(
                Locale.US,
                "Semana negativa:               %d / %d (%.2f%%)%n",
                weekNegative,
                total,
                pct(weekNegative, total)
        );

        System.out.println();

        System.out.println(
                "INTRAWEEK POTENTIAL"
        );

        System.out.printf(
                Locale.US,
                "Average HIGH vs Monday Open: %+7.2f%%%n",
                averageHigh * 100
        );

        System.out.printf(
                Locale.US,
                "Average LOW vs Monday Open:  %+7.2f%%%n",
                averageLow * 100
        );

        System.out.println();

        if (recovered > continuedLoss) {
            System.out.println(
                    "CONCLUSION: tras un lunes negativo, "
                            + "la semana tiende a remontar."
            );
        } else if (continuedLoss > recovered) {
            System.out.println(
                    "CONCLUSION: tras un lunes negativo, "
                            + "la semana tiende a continuar perdiendo."
            );
        } else {
            System.out.println(
                    "CONCLUSION: remontadas y continuaciones "
                            + "a la baja aparecen con igual frecuencia."
            );
        }
    }

    private static void printDetail(
            List<MondayNegativeWeekResult> results
    ) {
        System.out.println();
        System.out.println(
                "DETAIL"
        );

        System.out.println(
                "Week         Mon      WeekClose   Recovery    HIGH       LOW"
        );

        System.out.println(
                "----------------------------------------------------------------"
        );

        for (MondayNegativeWeekResult result : results) {

            System.out.printf(
                    Locale.US,
                    "%s   %+7.2f%%   %+8.2f%%   %+8.2f%%   %+7.2f%%   %+7.2f%%%n",
                    result.weekStart(),
                    result.mondayReturn() * 100,
                    result.weekReturn() * 100,
                    result.recoveryFromMondayClose() * 100,
                    result.highReturn() * 100,
                    result.lowReturn() * 100
            );
        }
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
