package com.alphapowertrading.simulator.core.report;

import com.alphapowertrading.simulator.core.broker.PositionSide;
import com.alphapowertrading.simulator.core.broker.Trade;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public record BacktestReport(
        double cash,
        double finalEquity,
        List<Trade> trades,
        List<Double> equityCurve,
        double averageDrawdown,
        double maxDrawdown,
        double cagr
) {

    private static final double ANNUALIZATION_FACTOR =
            Math.sqrt(252);

    private static final double TAX_RATE = 0.00;
    private static final int LOSS_CARRY_FORWARD_YEARS = 4;

    public BacktestReport {
        trades = List.copyOf(trades);
        equityCurve = List.copyOf(equityCurve);
    }

    public double avgProfitInPips() {
        return trades.stream()
                .mapToDouble(t -> {
                    double points = t.side() == PositionSide.LONG
                            ? t.exitPrice() - t.entryPrice()
                            : t.entryPrice() - t.exitPrice();

                    return points / 10.0;
                })
                .average()
                .orElse(0.0);
    }

    public double totalProfit() {
        return trades.stream()
                .mapToDouble(Trade::profit)
                .sum();
    }

    public long winningTrades() {
        return trades.stream()
                .filter(t -> t.profit() > 0)
                .count();
    }

    public long losingTrades() {
        return trades.stream()
                .filter(t -> t.profit() < 0)
                .count();
    }

    public long longTrades() {
        return trades.stream()
                .filter(t -> t.side() == PositionSide.LONG)
                .count();
    }

    public long shortTrades() {
        return trades.stream()
                .filter(t -> t.side() == PositionSide.SHORT)
                .count();
    }

    public double winPercentage() {
        if (trades.isEmpty()) {
            return 0;
        }

        return winningTrades() * 100.0 / trades.size();
    }

    public double averageWin() {
        return trades.stream()
                .filter(t -> t.profit() > 0)
                .mapToDouble(this::pnlPercentage)
                .average()
                .orElse(0);
    }

    public double averageLose() {
        return trades.stream()
                .filter(t -> t.profit() < 0)
                .mapToDouble(this::pnlPercentage)
                .average()
                .orElse(0);
    }

    private double pnlPercentage(Trade trade) {
        if (trade.side() == PositionSide.SHORT) {
            return (
                    (double) trade.entryPrice()
                            / trade.exitPrice()
                    - 1
            ) * 100;
        }

        return (
                (double) trade.exitPrice()
                        / trade.entryPrice()
                - 1
        ) * 100;
    }

    public double profitFactor() {
        double grossProfit =
                trades.stream()
                        .filter(t -> t.profit() > 0)
                        .mapToDouble(Trade::profit)
                        .sum();

        double grossLoss =
                trades.stream()
                        .filter(t -> t.profit() < 0)
                        .mapToDouble(Trade::profit)
                        .sum();

        if (grossLoss == 0) {
            return grossProfit > 0
                    ? Double.POSITIVE_INFINITY
                    : 0;
        }

        return grossProfit / Math.abs(grossLoss);
    }

    public double sharpeRatio() {
        if (equityCurve.size() < 2) {
            return 0;
        }

        double sumReturns = 0;
        double sumSquaredReturns = 0;
        int count = 0;

        for (int i = 1; i < equityCurve.size(); i++) {
            double previous = equityCurve.get(i - 1);
            double current = equityCurve.get(i);

            if (previous <= 0) {
                continue;
            }

            double dailyReturn =
                    current / previous - 1;

            sumReturns += dailyReturn;
            sumSquaredReturns +=
                    dailyReturn * dailyReturn;

            count++;
        }

        if (count < 2) {
            return 0;
        }

        double mean = sumReturns / count;

        double variance =
                sumSquaredReturns / count
                        - mean * mean;

        if (variance <= 0) {
            return 0;
        }

        return mean
                / Math.sqrt(variance)
                * ANNUALIZATION_FACTOR;
    }

    public double totalTaxes() {
        return taxCalculation().totalTaxes();
    }

    public double netFinalEquity() {
        return finalEquity - totalTaxes();
    }

    public double netTotalProfit() {
        return netFinalEquity() - initialCapital();
    }

    public double netCagr() {
        double initialCapital = initialCapital();
        double netEquity = netFinalEquity();
        double years = simulationYears();

        if (initialCapital <= 0
                || netEquity <= 0
                || years <= 0) {
            return 0;
        }

        return Math.pow(
                netEquity / initialCapital,
                1.0 / years
        ) - 1;
    }

    public double initialCapital() {
        return equityCurve.isEmpty()
                ? finalEquity
                : equityCurve.getFirst();
    }

    public List<AnnualTaxResult> annualTaxResults() {
        return taxCalculation().annualResults();
    }

    private TaxCalculation taxCalculation() {
        List<AnnualProfit> annualProfits =
                annualProfits();

        List<LossCarryForward> losses =
                new ArrayList<>();

        List<AnnualTaxResult> results =
                new ArrayList<>();

        double totalTaxes = 0;

        for (AnnualProfit annualProfit : annualProfits) {
            int year = annualProfit.year();
            double profit = annualProfit.profit();

            losses.removeIf(
                    loss -> year - loss.year()
                            > LOSS_CARRY_FORWARD_YEARS
            );

            if (profit < 0) {
                losses.add(
                        new LossCarryForward(
                                year,
                                -profit
                        )
                );

                results.add(
                        new AnnualTaxResult(
                                year,
                                profit,
                                0,
                                0,
                                0
                        )
                );

                continue;
            }

            double remainingProfit = profit;
            double compensatedLoss = 0;

            for (LossCarryForward loss : losses) {
                if (remainingProfit <= 0) {
                    break;
                }

                double compensation =
                        Math.min(
                                remainingProfit,
                                loss.remainingLoss()
                        );

                remainingProfit -= compensation;
                compensatedLoss += compensation;
                loss.consume(compensation);
            }

            losses.removeIf(
                    loss -> loss.remainingLoss() <= 0
            );

            double taxableProfit =
                    Math.max(0, remainingProfit);

            double tax =
                    taxableProfit * TAX_RATE;

            totalTaxes += tax;

            results.add(
                    new AnnualTaxResult(
                            year,
                            profit,
                            compensatedLoss,
                            taxableProfit,
                            tax
                    )
            );
        }

        return new TaxCalculation(
                totalTaxes,
                List.copyOf(results)
        );
    }

    private List<AnnualProfit> annualProfits() {
        Map<Integer, Double> profits =
                trades.stream()
                        .collect(
                                Collectors.groupingBy(
                                        trade ->
                                                trade.exitDate()
                                                        .getYear(),
                                        TreeMap::new,
                                        Collectors.summingDouble(
                                                Trade::profit
                                        )
                                )
                        );

        return profits.entrySet()
                .stream()
                .map(entry ->
                        new AnnualProfit(
                                entry.getKey(),
                                entry.getValue()
                        )
                )
                .sorted(
                        Comparator.comparingInt(
                                AnnualProfit::year
                        )
                )
                .toList();
    }

    private double simulationYears() {
        if (trades.isEmpty()) {
            return 0;
        }

        var firstDate =
                trades.stream()
                        .map(Trade::entryDate)
                        .min(Comparator.naturalOrder())
                        .orElseThrow();

        var lastDate =
                trades.stream()
                        .map(Trade::exitDate)
                        .max(Comparator.naturalOrder())
                        .orElseThrow();

        return ChronoUnit.DAYS.between(
                firstDate,
                lastDate
        ) / 365.25;
    }

    private record AnnualProfit(
            int year,
            double profit
    ) {
    }

    public record AnnualTaxResult(
            int year,
            double annualProfit,
            double compensatedLoss,
            double taxableProfit,
            double tax
    ) {
    }

    private record TaxCalculation(
            double totalTaxes,
            List<AnnualTaxResult> annualResults
    ) {
    }

    private static class LossCarryForward {

        private final int year;
        private double remainingLoss;

        private LossCarryForward(
                int year,
                double remainingLoss
        ) {
            this.year = year;
            this.remainingLoss = remainingLoss;
        }

        private int year() {
            return year;
        }

        private double remainingLoss() {
            return remainingLoss;
        }

        private void consume(double amount) {
            remainingLoss -= amount;
        }
    }
}
