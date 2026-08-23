package com.alphapowertrading.simulator.analysis.sma;

import com.alphapowertrading.simulator.core.loader.MarketDataCsvLoader;
import com.alphapowertrading.simulator.core.market.MarketData;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * Standalone runner for parameter sweeps of the SMA mean-reverting setup.
 */
@SpringBootApplication(scanBasePackages = "com.alphapowertrading.simulator")
public class SMAMeanRevertingRunner {

    private static final String DEFAULT_FILE =
            "data/local/EURUSD_15m.csv";

    private static final String ALL_RESULTS_FILE =
            "output/sma/SMAMeanReverting_all_results.csv";

    private static final String PROMISING_RESULTS_FILE =
            "output/sma/SMAMeanReverting_promising_results.csv";

    private static final double MINIMUM_OPERATIONS_PER_YEAR = 0.0;
    private static final double MINIMUM_AVERAGE_PIPS = 2.0;
    private static final double MINIMUM_PROFIT_FACTOR = 1.20;
    private static final int MINIMUM_OPERATIONS = 50;
    private static final double MINIMUM_R_DD_RATIO = 0.25;

    public static void main(String[] args) throws Exception {

        String file =
                args.length == 0
                        ? DEFAULT_FILE
                        : args[0];

        printHeader(file);

        ApplicationContext context =
                SpringApplication.run(
                        SMAMeanRevertingRunner.class,
                        args);

        List<MarketDataCsvLoader> loaders =
                context
                        .getBeansOfType(MarketDataCsvLoader.class)
                        .values()
                        .stream()
                        .toList();

        MarketData marketData =
                loadMarketData(
                        Path.of(file),
                        loaders);

        System.out.println(
                "Candles loaded: "
                        + marketData.size());

        System.out.println();

        SMAMeanRevertingAnalyzer analyzer =
                new SMAMeanRevertingAnalyzer();

        List<SMAMeanRevertingResult> allResults =
                new ArrayList<>();

        List<SMAMeanRevertingResult> promisingResults =
                new ArrayList<>();

        /*
         * ============================================================
         * SMA LENGTHS
         * ============================================================
         */
        int[] smaLengths = {
                2,4,8,12,16,20,24,28,32,36,40,44,48,52
        };

        /*
         * ============================================================
         * DISTANCES
         * ============================================================
         */
        double[] distances = {
                10,20,30,40,50,60,70,80,90,100
        };

        /*
         * ============================================================
         * TAKE PROFITS
         * ============================================================
         */
        double[] tps = {
                999999
        };

        /*
         * ============================================================
         * STOP LOSSES
         * ============================================================
         */
        double[] sls = {
               999
        };

        /*
         * ============================================================
         * MAXIMUM HOLDING PERIOD
         * ============================================================
         */
        int[] exits = {
                2,4,6,8,12,16,20,24,28,32,36,40,44,48,52
        };

        /*
         * ============================================================
         * TRANSACTION COST
         * ============================================================
         */
        double transactionCostPips = 0.75;

        /*
         * ============================================================
         * TRADING HOURS
         * ============================================================
         *
         * Each element represents one independent hour configuration.
         *
         * Examples:
         *
         * {11}
         * {9}
         * {9, 10}
         * {9, 10, 11}
         * {6, 9, 14}
         *
         * Each configuration is tested independently.
         */
        int[][] tradingHourConfigurations = {
                {0},
                /*
                {0},
                {1},
                {2},
                {3},
                {4},
                {5},
                {6},
                {7},
                {8},
                {9},
                {10},
                {11},
                {12},
                {13},
                {14},
                {15},
                {16},
                {17},
                {18},
                {19},
                {20},
                {21},
                {22},
                {23},
            */

                // Examples:
                // {9},
                // {10},
                // {9, 10},
                // {9, 10, 11},
                // {6, 9, 14}
        };

        long totalTests =
                (long)
                        smaLengths.length
                        * distances.length
                        * tps.length
                        * sls.length
                        * exits.length
                        * tradingHourConfigurations.length;

        printConfiguration(
                totalTests,
                tradingHourConfigurations.length,
                transactionCostPips);

        long completed = 0;
        long startTime = System.currentTimeMillis();
        int lastProgress = -1;

        /*
         * ============================================================
         * PARAMETER SWEEP
         * ============================================================
         */
        for (int[] configuredHours :
                tradingHourConfigurations) {

            int[] tradingHours =
                    createTradingHours(
                            configuredHours);

            for (int sma : smaLengths) {

                for (double distance : distances) {

                    for (double tp : tps) {

                        for (double sl : sls) {

                            for (int exit : exits) {

                                SMAMeanRevertingParameters parameters =
                                        new SMAMeanRevertingParameters(
                                                sma,
                                                distance,
                                                tp,
                                                sl,
                                                exit,
                                                transactionCostPips,
                                                tradingHours);

                                SMAMeanRevertingResult result =
                                        analyzer.analyze(
                                                marketData,
                                                parameters);

                                allResults.add(result);

                                if (passesFilters(result)) {

                                    promisingResults.add(result);

                                    printPromisingResult(result);
                                }

                                completed++;

                                int progress =
                                        (int)
                                                ((completed * 100)
                                                        / totalTests);

                                if (progress != lastProgress
                                        && progress % 10 == 0) {

                                    long elapsed =
                                            System.currentTimeMillis()
                                                    - startTime;

                                    System.out.printf(
                                            Locale.US,
                                            "Progress: %3d%% (%d/%d) - %d ms%n",
                                            progress,
                                            completed,
                                            totalTests,
                                            elapsed);

                                    lastProgress = progress;
                                }
                            }
                        }
                    }
                }
            }
        }

        long elapsed =
                System.currentTimeMillis()
                        - startTime;

        printSummary(
                completed,
                elapsed,
                allResults,
                promisingResults);

        /*
         * ============================================================
         * SORTING
         * ============================================================
         *
         * Primary:
         *     R/DD
         *
         * Secondary:
         *     Profit Factor
         *
         * Tertiary:
         *     Sharpe
         */
        promisingResults.sort(
                Comparator
                        .comparingDouble(
                                SMAMeanRevertingRunner::calculateReturnDd)
                        .reversed()
                        .thenComparing(
                                Comparator
                                        .comparingDouble(
                                                SMAMeanRevertingResult::profitFactor)
                                        .reversed())
                        .thenComparing(
                                Comparator
                                        .comparingDouble(
                                                SMAMeanRevertingResult::sharpeRatio)
                                        .reversed()));

        System.out.println();

        printResults(
                promisingResults);

        /*
         * ============================================================
         * CSV
         * ============================================================
         */
        writeResultsCsv(
                allResults,
                Path.of(ALL_RESULTS_FILE));

        writeResultsCsv(
                promisingResults,
                Path.of(PROMISING_RESULTS_FILE));
    }

    /**
     * R/DD =
     *
     * AVG_PIPS * OPS/YEAR / MAX_DD
     *
     * This is used as the primary ranking metric.
     */
    private static double calculateReturnDd(
            SMAMeanRevertingResult result) {

        double maxDrawdown =
                result.maxDrawdownPips();

        if (maxDrawdown <= 0.0) {

            if (result.averagePips() > 0.0) {
                return Double.POSITIVE_INFINITY;
            }

            return 0.0;
        }

        double annualPips =
                result.averagePips()
                        * result.operationsPerYear();

        return annualPips / maxDrawdown;
    }

    private static boolean passesFilters(
            SMAMeanRevertingResult result) {

        double rddratio = result.averagePips()*result.operationsPerYear()/result.maxDrawdownPips();
        return result.operationsPerYear()
                >= MINIMUM_OPERATIONS_PER_YEAR
                && result.averagePips()
                >= MINIMUM_AVERAGE_PIPS
                && result.profitFactor()
                >= MINIMUM_PROFIT_FACTOR
                && result.operations()
                >= MINIMUM_OPERATIONS
                && rddratio>=MINIMUM_R_DD_RATIO
                ;
    }

    private static void printPromisingResult(
            SMAMeanRevertingResult result) {

        SMAMeanRevertingParameters parameters =
                result.parameters();

        String hours =
                formatTradingHours(
                        parameters.tradingHours());

        System.out.printf(
                Locale.US,
                "PROMISING "
                        + "hours=%s "
                        + "sma=%d "
                        + "distance=%.2f "
                        + "tp=%.2f "
                        + "sl=%.2f "
                        + "exit=%d "
                        + "cost=%.2f "
                        + "trades=%d "
                        + "avgPips=%.2f "
                        + "PF=%.2f "
                        + "Sharpe=%.2f "
                        + "DD=%.2f "
                        + "R/DD=%.4f%n",
                hours,
                parameters.smaLength(),
                parameters.distancePips(),
                parameters.tpPips(),
                parameters.slPips(),
                parameters.exitAfterCandles(),
                parameters.transactionCostPips(),
                result.operations(),
                result.averagePips(),
                result.profitFactor(),
                result.sharpeRatio(),
                result.maxDrawdownPips(),
                calculateReturnDd(result));
    }

    private static int[] createTradingHours(
            int[] configuredHours) {

        if (configuredHours == null
                || configuredHours.length == 0) {

            throw new IllegalArgumentException(
                    "At least one trading hour must be configured");
        }

        int[] tradingHours =
                new int[24];

        for (int hour : configuredHours) {

            if (hour < 0 || hour > 23) {

                throw new IllegalArgumentException(
                        "Invalid trading hour: "
                                + hour);
            }

            tradingHours[hour] = 1;
        }

        return tradingHours;
    }

    private static String formatTradingHours(
            int[] tradingHours) {

        StringBuilder result =
                new StringBuilder();

        boolean first = true;

        for (int hour = 0;
             hour < tradingHours.length;
             hour++) {

            if (tradingHours[hour] != 1) {
                continue;
            }

            if (!first) {
                result.append("-");
            }

            result.append(
                    String.format(
                            Locale.US,
                            "%02d",
                            hour));

            first = false;
        }

        return result.toString();
    }

    private static MarketData loadMarketData(
            Path file,
            List<MarketDataCsvLoader> loaders)
            throws IOException {

        String header;

        try (BufferedReader reader =
                     Files.newBufferedReader(
                             file,
                             StandardCharsets.UTF_8)) {

            header =
                    reader.readLine();
        }

        if (header == null
                || header.isBlank()) {

            throw new IllegalArgumentException(
                    "CSV file is empty: "
                            + file);
        }

        System.out.println(
                "header: "
                        + header);

        MarketDataCsvLoader selectedLoader =
                loaders.stream()
                        .filter(
                                loader ->
                                        loader.supports(
                                                header))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "No CSV loader supports header: "
                                                        + header));

        System.out.println(
                "CSV loader: "
                        + selectedLoader
                        .getClass()
                        .getSimpleName());

        return selectedLoader.load(file);
    }

    private static void printSummary(
            long completed,
            long elapsed,
            List<SMAMeanRevertingResult> allResults,
            List<SMAMeanRevertingResult> promisingResults) {

        System.out.println();

        System.out.println(
                "========================================");

        System.out.println(
                "Analysis completed");

        System.out.println(
                "========================================");

        System.out.println(
                "Tests: "
                        + completed);

        System.out.println(
                "Elapsed: "
                        + elapsed
                        + " ms");

        System.out.println(
                "All results: "
                        + allResults.size());

        System.out.println(
                "Results passing filters: "
                        + promisingResults.size());
    }

    private static void printResults(
            List<SMAMeanRevertingResult> results) {

        if (results.isEmpty()) {

            System.out.println(
                    "No parameter combination passed "
                            + "the filters.");

            return;
        }

        System.out.println(
                "HOURS | SMA | DIST | TP | SL | EXIT | "
                        + "COST | OPS/YEAR | WIN% | AVG_PIPS | "
                        + "MEDIAN | AVG_MAE | MAE95 | AVG_MFE | "
                        + "MFE95 | PF | SHARPE | DD | R/DD");

        System.out.println(
                "--------------------------------------------------------------------------------------------------------------------------------");

        for (SMAMeanRevertingResult result :
                results) {

            SMAMeanRevertingParameters parameters =
                    result.parameters();

            String hours =
                    formatTradingHours(
                            parameters.tradingHours());

            System.out.printf(
                    Locale.US,
                    "%s | "
                            + "%3d | "
                            + "%5.2f | "
                            + "%7.2f | "
                            + "%7.2f | "
                            + "%4d | "
                            + "%4.2f | "
                            + "%8.1f | "
                            + "%5.1f | "
                            + "%8.2f | "
                            + "%6.2f | "
                            + "%8.2f | "
                            + "%7.2f | "
                            + "%8.2f | "
                            + "%7.2f | "
                            + "%5.2f | "
                            + "%6.2f | "
                            + "%8.2f | "
                            + "%7.4f%n",
                    hours,
                    parameters.smaLength(),
                    parameters.distancePips(),
                    parameters.tpPips(),
                    parameters.slPips(),
                    parameters.exitAfterCandles(),
                    parameters.transactionCostPips(),
                    result.operationsPerYear(),
                    result.winRate(),
                    result.averagePips(),
                    result.medianPips(),
                    result.averageMaePips(),
                    result.maeP95Pips(),
                    result.averageMfePips(),
                    result.mfeP95Pips(),
                    result.profitFactor(),
                    result.sharpeRatio(),
                    result.maxDrawdownPips(),
                    calculateReturnDd(result));
        }

        System.out.println();

        System.out.println(
                "Results passing filters: "
                        + results.size());
    }

    private static void writeResultsCsv(
            List<SMAMeanRevertingResult> results,
            Path outputFile)
            throws IOException {

        Path parent =
                outputFile.getParent();

        if (parent != null) {
            Files.createDirectories(
                    parent);
        }

        try (BufferedWriter writer =
                     Files.newBufferedWriter(
                             outputFile,
                             StandardCharsets.UTF_8)) {

            writer.write(
                    "hours,"
                            + "smaLength,"
                            + "distancePips,"
                            + "tpPips,"
                            + "slPips,"
                            + "exitAfterCandles,"
                            + "transactionCostPips,"
                            + "operations,"
                            + "operationsPerYear,"
                            + "winningOperations,"
                            + "winRate,"
                            + "netPips,"
                            + "averagePips,"
                            + "medianPips,"
                            + "averageMaePips,"
                            + "maeP95Pips,"
                            + "averageMfePips,"
                            + "mfeP95Pips,"
                            + "profitFactor,"
                            + "sharpeRatio,"
                            + "maxDrawdownPips,"
                            + "returnDd");

            writer.newLine();

            for (SMAMeanRevertingResult result :
                    results) {

                SMAMeanRevertingParameters parameters =
                        result.parameters();

                String hours =
                        formatTradingHours(
                                parameters.tradingHours());

                writer.write(
                        String.format(
                                Locale.US,
                                "%s,%d,%.4f,%.4f,%.4f,%d,"
                                        + "%.4f,%d,%.4f,%d,"
                                        + "%.4f,%.4f,%.4f,%.4f,"
                                        + "%.4f,%.4f,%.4f,%.4f,"
                                        + "%.4f,%.4f,%.6f",
                                hours,
                                parameters.smaLength(),
                                parameters.distancePips(),
                                parameters.tpPips(),
                                parameters.slPips(),
                                parameters.exitAfterCandles(),
                                parameters.transactionCostPips(),
                                result.operations(),
                                result.operationsPerYear(),
                                result.winningOperations(),
                                result.winRate(),
                                result.netPips(),
                                result.averagePips(),
                                result.medianPips(),
                                result.averageMaePips(),
                                result.maeP95Pips(),
                                result.averageMfePips(),
                                result.mfeP95Pips(),
                                result.profitFactor(),
                                result.sharpeRatio(),
                                result.maxDrawdownPips(),
                                calculateReturnDd(result)));

                writer.newLine();
            }
        }

        System.out.println(
                "CSV written: "
                        + outputFile.toAbsolutePath()
                        + " ("
                        + results.size()
                        + " rows)");
    }

    private static void printHeader(
            String file) {

        System.out.println(
                "========================================");

        System.out.println(
                "SMA Mean Reverting Analyzer");

        System.out.println(
                "========================================");

        System.out.println(
                "File: "
                        + file);

        System.out.println();
    }

    private static void printConfiguration(
            long totalTests,
            int tradingHourConfigurations,
            double transactionCostPips) {

        System.out.println(
                "Trading hour configurations: "
                        + tradingHourConfigurations);

        System.out.println(
                "Parameter combinations: "
                        + totalTests);

        System.out.println(
                "Minimum operations/year: "
                        + MINIMUM_OPERATIONS_PER_YEAR);

        System.out.println(
                "Minimum average pips: "
                        + MINIMUM_AVERAGE_PIPS);

        System.out.println(
                "Minimum profit factor: "
                        + MINIMUM_PROFIT_FACTOR);

        System.out.println(
                "Minimum operations: "
                        + MINIMUM_OPERATIONS);

        System.out.println(
                "Transaction cost: "
                        + String.format(
                        Locale.US,
                        "%.2f",
                        transactionCostPips)
                        + " pips");

        System.out.println(
                "Ranking: "
                        + "AVG_PIPS * OPS/YEAR / MAX_DD");

        System.out.println();
    }
}