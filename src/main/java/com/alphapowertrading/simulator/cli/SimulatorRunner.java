package com.alphapowertrading.simulator.cli;

import com.alphapowertrading.simulator.config.SimulatorProperties;
import com.alphapowertrading.simulator.core.engine.BacktestEngine;
import com.alphapowertrading.simulator.core.loader.CsvLoader;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.strategy.Strategy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class SimulatorRunner implements CommandLineRunner {

    private final CsvLoader csvLoader;
    private final SimulatorProperties properties;
    private final Strategy strategy;

    public SimulatorRunner(
            CsvLoader csvLoader,
            SimulatorProperties properties,
            Strategy strategy
    ) {
        this.csvLoader = csvLoader;
        this.properties = properties;
        this.strategy = strategy;
    }

    @Override
    public void run(String... args) throws Exception {
        Path file = Path.of(
                properties.dataDirectory(),
                properties.symbol() + ".csv"
        );

        MarketData marketData = csvLoader.load(file);

        System.out.printf(
                "Loaded %d candles for %s (%s -> %s)%n",
                marketData.size(),
                properties.symbol(),
                marketData.get(0).date(),
                marketData.get(marketData.size() - 1).date()
        );

        BacktestEngine engine = new BacktestEngine(properties.initialCapital());
        var report = engine.run(marketData, strategy);

        System.out.printf(
                "Backtest finished. Final equity: %.2f | Trades: %d | PnL: %.2f%n",
                report.finalEquity(),
                report.trades().size(),
                report.totalProfit()
        );
    }
}
