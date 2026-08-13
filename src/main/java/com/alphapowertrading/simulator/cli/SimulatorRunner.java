package com.alphapowertrading.simulator.cli;

import com.alphapowertrading.simulator.config.SimulatorProperties;
import com.alphapowertrading.simulator.core.chart.JFreeChartGenerator;
import com.alphapowertrading.simulator.core.engine.BacktestEngine;
import com.alphapowertrading.simulator.core.loader.CsvLoader;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.report.BacktestReport;
import com.alphapowertrading.simulator.core.strategy.Strategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

@Component
public class SimulatorRunner implements CommandLineRunner {

    private final CsvLoader csvLoader;
    private final SimulatorProperties properties;
    private final Map<String, Strategy> strategies;
    private final JFreeChartGenerator chartGenerator;
    private final double commissionRate;

    public SimulatorRunner(
            CsvLoader csvLoader,
            SimulatorProperties properties,
            Map<String, Strategy> strategies,
            JFreeChartGenerator chartGenerator,
            @Value("${simulator.commission-rate:0.0}") double commissionRate
    ) {
        this.csvLoader = csvLoader;
        this.properties = properties;
        this.strategies = strategies;
        this.chartGenerator = chartGenerator;
        this.commissionRate = commissionRate;
    }

    @Override
    public void run(String... args) throws Exception {
        Strategy strategy = strategies.get(properties.strategy());

        if (strategy == null) {
            throw new IllegalArgumentException(
                    "Unknown strategy: " + properties.strategy()
                            + ". Available strategies: " + strategies.keySet()
            );
        }

        Path file = Path.of(properties.dataDirectory(), properties.symbol() + ".csv");
        MarketData marketData = csvLoader.load(file);

        System.out.printf(
                "Loaded %d candles for %s (%s -> %s)%n",
                marketData.size(),
                properties.symbol(),
                marketData.get(0).date(),
                marketData.get(marketData.size() - 1).date()
        );

        System.out.printf("Strategy: %s%n", properties.strategy());

        BacktestEngine engine = new BacktestEngine(
                properties.initialCapital(),
                properties.showTrades(),
                properties.showLossWeek(),
                properties.lossWeekThreshold(),
                properties.showMaxDd(),
                commissionRate
        );

        BacktestReport report = engine.run(marketData, strategy);
        printReport(report);

        Path chartDirectory = Path.of("output", "charts");

        chartGenerator.generate(
                properties.symbol(),
                properties.initialCapital(),
                marketData,
                report,
                chartDirectory
        );

        System.out.printf(
                "Yearly charts generated in: %s%n",
                chartDirectory.toAbsolutePath()
        );
    }

    private void printReport(BacktestReport report) {
        System.out.println();
        System.out.println("================ BACKTEST RESULT ================");

        System.out.printf("Final equity: %.2f%n", report.finalEquity());
        System.out.printf("Trades: %d%n", report.trades().size());
        System.out.printf("PnL: %.2f%n", report.totalProfit());
        System.out.printf("Factor: %.2f%n", report.finalEquity() / properties.initialCapital());
        System.out.printf("AvgDD: %.2f%%%n", report.averageDrawdown() * 100);
        System.out.printf("MaxDD: %.2f%%%n", report.maxDrawdown() * 100);
        System.out.printf("CAGR: %.2f%%%n", report.cagr() * 100);
        System.out.printf("Win%%: %.2f%%%n", report.winPercentage());
        System.out.printf("AvgWin: %.2f%%%n", report.averageWin());
        System.out.printf("AvgLose: %.2f%%%n", report.averageLose());
        System.out.printf("Profit Factor: %.2f%n", report.profitFactor());
        System.out.printf("Sharpe: %.2f%n", report.sharpeRatio());
        System.out.printf("Taxes: %.2f%n", report.totalTaxes());
        System.out.printf("Net Final Equity: %.2f%n", report.netFinalEquity());
        System.out.printf("Net Total Profit: %.2f%n", report.netTotalProfit());
        System.out.printf("Net CAGR: %.2f%%%n", report.netCagr() * 100);

        System.out.println("==================================================");
    }
}