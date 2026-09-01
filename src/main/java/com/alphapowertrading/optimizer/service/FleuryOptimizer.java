package com.alphapowertrading.optimizer.service;

import com.alphapowertrading.optimizer.config.OptimizationProperties;
import com.alphapowertrading.optimizer.model.OptimizationResult;
import com.alphapowertrading.simulator.core.engine.BacktestEngine;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.report.BacktestReport;
import com.alphapowertrading.simulator.strategy.OPPWFleuryStrategy;
import java.util.ArrayList;
import java.util.List;

import com.alphapowertrading.simulator.strategy.OPPWFleuryV2Strategy;
import org.springframework.stereotype.Component;

@Component
public class FleuryOptimizer {

    public List<OptimizationResult> optimize(
            MarketData marketData,
            OptimizationProperties properties) {

        List<Double> tps = values(properties.tp());
        List<Double> tphs = values(properties.tph());
        List<Double> sls = values(properties.sl());
        List<Double> openGaps = values(properties.openGap());

        int total = tps.size() * tphs.size() * sls.size() * openGaps.size();
        int current = 0;

        List<OptimizationResult> results =
                new ArrayList<>(total);

        for (double tp : tps) {
            for (double tph : tphs) {
                for (double sl : sls) {
                    for (double openGap : openGaps) {
                        current++;

                        OPPWFleuryV2Strategy strategy =
                                new OPPWFleuryV2Strategy(tp, tph, sl,openGap);

                        BacktestEngine engine =
                                new BacktestEngine(
                                        properties.initialCapital(),
                                        false,
                                        false,
                                        0.0,
                                        false,
                                        properties.commissionRate(),
                                        properties.startDate(),
                                        properties.endDate()
                                );

                        BacktestReport report =
                                engine.run(marketData, strategy);

                        double maxDd = report.maxDrawdown();

                        double calmar =
                                maxDd == 0.0
                                        ? 0.0
                                        : report.cagr() / Math.abs(maxDd);

                        results.add(new OptimizationResult(
                                tp,
                                tph,
                                sl,
                                openGap,
                                report.sharpeRatio(),
                                report.cagr(),
                                maxDd,
                                calmar,
                                report.finalEquity(),
                                report.trades().size(),
                                report.winPercentage()
                        ));

                        if (properties.showProgress()
                                && (current % 100 == 0 || current == total)){
                                System.out.printf(
                                        "Progress: %d/%d%n",
                                        current,
                                        total
                                );
                        }
                    }
                }
            }
        }

        return results;
    }

    private List<Double> values(
            OptimizationProperties.Parameters parameters) {

        if (parameters.step() <= 0) {
            throw new IllegalArgumentException(
                    "Optimization step must be greater than zero"
            );
        }

        if (parameters.max() < parameters.min()) {
            throw new IllegalArgumentException(
                    "Optimization max must be >= min"
            );
        }

        List<Double> values = new ArrayList<>();

        for (double value = parameters.min();
             value <= parameters.max()
                     + parameters.step() * 1e-9;
             value += parameters.step()) {
            values.add(value);
        }

        return values;
    }
}
