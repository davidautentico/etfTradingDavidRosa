package com.alphapowertrading.walkforward.service;

import com.alphapowertrading.simulator.core.engine.BacktestEngine;
import com.alphapowertrading.simulator.core.loader.CsvLoader;
import com.alphapowertrading.simulator.core.loader.CsvParser;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.report.BacktestReport;
import com.alphapowertrading.simulator.strategy.OPPWFleuryStrategy;
import com.alphapowertrading.walkforward.config.WalkForwardProperties;
import com.alphapowertrading.walkforward.csv.WalkForwardMonthlyCsvWriter;
import com.alphapowertrading.walkforward.model.WalkForwardMonthlyResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class WalkForwardMonthlyRunner {

    private final WalkForwardProperties properties;
    private final WalkForwardMonthlyCsvWriter writer;

    public WalkForwardMonthlyRunner(
            WalkForwardProperties properties,
            WalkForwardMonthlyCsvWriter writer
    ) {
        this.properties = properties;
        this.writer = writer;
    }

    public void run() {
        Path file = Path.of(
                properties.dataDirectory(),
                properties.symbol() + ".csv"
        );

        try {
            CsvLoader loader = new CsvParser();
            MarketData data = loader.load(file);

            if (data.size() == 0) {
                throw new IllegalStateException(
                        "Market data is empty: " + file
                );
            }

            System.out.printf(
                    "Loaded %d candles for %s (%s -> %s)%n",
                    data.size(),
                    properties.symbol(),
                    data.get(0).date(),
                    data.get(data.size() - 1).date()
            );

            List<WalkForwardMonthlyResult> results = run(data);

            String outputFile = buildOutputFile();
            Path outputPath = Path.of(outputFile);

            Files.deleteIfExists(outputPath);

            writer.write(outputFile, results);

            System.out.printf(
                    "Completed %d walk-forward windows%n",
                    results.size()
            );

            System.out.println(
                    "Output: " + outputPath.toAbsolutePath()
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to process walk-forward data",
                    e
            );
        }
    }

    private String buildOutputFile() {
        return Path.of(
                properties.outputDirectory(),
                String.format(
                        "%s_IS%d_OOS%d_STEP%d.csv",
                        properties.symbol(),
                        properties.inSampleMonths(),
                        properties.outOfSampleMonths(),
                        properties.stepMonths()
                )
        ).toString();
    }

    private List<WalkForwardMonthlyResult> run(MarketData data) {
        validate(properties);

        List<WalkForwardMonthlyResult> results = new ArrayList<>();

        var start = data.get(0).date();
        var last = data.get(data.size() - 1).date();

        double equity = properties.initialCapital();
        int window = 0;

        while (true) {
            var oosStart = start.plusMonths(
                    properties.inSampleMonths()
            );

            var oosEnd = oosStart
                    .plusMonths(properties.outOfSampleMonths())
                    .minusDays(1);

            if (oosEnd.isAfter(last)) {
                break;
            }

            MarketData is = between(
                    data,
                    start,
                    oosStart.minusDays(1)
            );

            MarketData oos = between(
                    data,
                    oosStart,
                    oosEnd
            );

            if (is.size() == 0 || oos.size() == 0) {
                break;
            }

            Best best = best(is);
            double initialEquity = equity;

            BacktestReport report = backtest(
                    oos,
                    best.tp,
                    best.tph,
                    best.sl,
                    initialEquity
            );

            equity = report.finalEquity();

            double returnPct = initialEquity == 0
                    ? 0
                    : equity / initialEquity - 1;

            results.add(new WalkForwardMonthlyResult(
                    ++window,
                    is.get(0).date(),
                    is.get(is.size() - 1).date(),
                    oos.get(0).date(),
                    oos.get(oos.size() - 1).date(),
                    best.tp,
                    best.tph,
                    best.sl,
                    initialEquity,
                    equity,
                    returnPct,
                    report.trades().size(),
                    report.winPercentage()
            ));

            if (properties.showProgress()) {
                System.out.printf(
                        "WFA %d | IS %s -> %s | OOS %s -> %s | "
                                + "TP %.3f%% TPH %.3f%% SL %.3f%% | "
                                + "Return %.2f%% | Equity %.2f%n",
                        window,
                        is.get(0).date(),
                        is.get(is.size() - 1).date(),
                        oos.get(0).date(),
                        oos.get(oos.size() - 1).date(),
                        best.tp * 100,
                        best.tph * 100,
                        best.sl * 100,
                        returnPct * 100,
                        equity
                );
            }

            start = start.plusMonths(properties.stepMonths());
        }

        return results;
    }

    private Best best(MarketData data) {
        Best best = null;

        for (double tp : values(properties.optimization().tp())) {
            for (double tph : values(properties.optimization().tph())) {
                if (tph < tp) {
                    continue;
                }

                for (double sl : values(properties.optimization().sl())) {
                    BacktestReport report = backtest(
                            data,
                            tp,
                            tph,
                            sl,
                            properties.initialCapital()
                    );

                    Best candidate = new Best(
                            tp,
                            tph,
                            sl,
                            report
                    );

                    if (best == null || better(candidate, best)) {
                        best = candidate;
                    }
                }
            }
        }

        if (best == null) {
            throw new IllegalStateException(
                    "No valid parameter combination"
            );
        }

        return best;
    }

    private boolean better(Best a, Best b) {
        double difference =
                a.report.sharpeRatio() - b.report.sharpeRatio();

        if (Math.abs(difference) > 1e-9) {
            return difference > 0;
        }

        difference =
                a.report.cagr() - b.report.cagr();

        if (Math.abs(difference) > 1e-9) {
            return difference > 0;
        }

        return Math.abs(a.report.maxDrawdown())
                < Math.abs(b.report.maxDrawdown());
    }

    private BacktestReport backtest(
            MarketData data,
            double tp,
            double tph,
            double sl,
            double initialCapital
    ) {
        return new BacktestEngine(
                initialCapital,
                false,
                false,
                0,
                false,
                properties.commissionRate()
        ).run(
                data,
                new OPPWFleuryStrategy(tp, tph, sl)
        );
    }

    private MarketData between(
            MarketData data,
            java.time.LocalDate start,
            java.time.LocalDate end
    ) {
        List<Candle> candles = data.candles().stream()
                .filter(candle ->
                        !candle.date().isBefore(start)
                                && !candle.date().isAfter(end)
                )
                .toList();

        return new MarketData(candles);
    }

    private List<Double> values(
            WalkForwardProperties.Range range
    ) {
        if (range.step() <= 0 || range.max() < range.min()) {
            throw new IllegalArgumentException(
                    "Invalid optimization range"
            );
        }

        List<Double> values = new ArrayList<>();

        for (double value = range.min();
             value <= range.max() + range.step() * 1e-9;
             value += range.step()) {
            values.add(value);
        }

        return values;
    }

    private void validate(WalkForwardProperties p) {
        if (p.inSampleMonths() <= 0
                || p.outOfSampleMonths() <= 0
                || p.stepMonths() <= 0) {
            throw new IllegalArgumentException(
                    "IS, OOS and step must be > 0"
            );
        }
    }

    private record Best(
            double tp,
            double tph,
            double sl,
            BacktestReport report
    ) {
    }
}