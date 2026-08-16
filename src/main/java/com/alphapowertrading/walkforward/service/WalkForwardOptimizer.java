package com.alphapowertrading.walkforward.service;

import com.alphapowertrading.simulator.core.engine.BacktestEngine;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.report.BacktestReport;
import com.alphapowertrading.simulator.strategy.OPPWFleuryStrategy;
import com.alphapowertrading.walkforward.config.WalkForwardProperties;
import com.alphapowertrading.walkforward.model.WalkForwardResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class WalkForwardOptimizer {

    public List<WalkForwardResult> run(
            MarketData data,
            WalkForwardProperties p
    ) {
        validate(p);

        List<WalkForwardResult> out = new ArrayList<>();
        LocalDate start = data.get(0).date();
        LocalDate last = data.get(data.size() - 1).date();
        int n = 0;

        while (true) {
            LocalDate oosStart = start.plusMonths(p.inSampleMonths());
            LocalDate oosEnd = oosStart
                    .plusMonths(p.outOfSampleMonths())
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

            Best best = best(is, p);

            BacktestReport r = backtest(
                    oos,
                    best.tp,
                    best.tph,
                    best.sl,
                    p
            );

            double ratio = best.report.cagr() == 0
                    ? 0
                    : r.cagr() / best.report.cagr();

            out.add(new WalkForwardResult(
                    ++n,
                    is.get(0).date(),
                    is.get(is.size() - 1).date(),
                    oos.get(0).date(),
                    oos.get(oos.size() - 1).date(),
                    best.tp,
                    best.tph,
                    best.sl,
                    best.report.cagr(),
                    best.report.sharpeRatio(),
                    best.report.maxDrawdown(),
                    r.cagr(),
                    r.sharpeRatio(),
                    r.maxDrawdown(),
                    r.finalEquity(),
                    r.trades().size(),
                    r.winPercentage(),
                    ratio
            ));

            if (p.showProgress()) {
                System.out.printf(
                        "WFA %d | IS %s -> %s | OOS %s -> %s | "
                                + "TP %.3f%% TPH %.3f%% SL %.3f%% | "
                                + "OOS CAGR %.2f%% Sharpe %.2f MaxDD %.2f%%%n",
                        n,
                        is.get(0).date(),
                        is.get(is.size() - 1).date(),
                        oos.get(0).date(),
                        oos.get(oos.size() - 1).date(),
                        best.tp * 100,
                        best.tph * 100,
                        best.sl * 100,
                        r.cagr() * 100,
                        r.sharpeRatio(),
                        r.maxDrawdown() * 100
                );
            }

            start = start.plusMonths(p.stepMonths());
        }

        return out;
    }

    private Best best(
            MarketData data,
            WalkForwardProperties p
    ) {
        Best best = null;

        for (double tp : values(p.optimization().tp())) {
            for (double tph : values(p.optimization().tph())) {
                if (tph < tp) {
                    continue;
                }

                for (double sl : values(p.optimization().sl())) {
                    BacktestReport report = backtest(
                            data,
                            tp,
                            tph,
                            sl,
                            p
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
            WalkForwardProperties p
    ) {
        return new BacktestEngine(
                p.initialCapital(),
                false,
                false,
                0,
                false,
                p.commissionRate()
        ).run(
                data,
                new OPPWFleuryStrategy(tp, tph, sl)
        );
    }

    private MarketData between(
            MarketData data,
            LocalDate start,
            LocalDate end
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
