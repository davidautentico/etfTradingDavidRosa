package com.alphapowertrading.optimizer;

import com.alphapowertrading.optimizer.config.OptimizationProperties;
import com.alphapowertrading.optimizer.csv.OptimizationCsvWriter;
import com.alphapowertrading.optimizer.model.OptimizationResult;
import com.alphapowertrading.optimizer.service.FleuryOptimizer;
import com.alphapowertrading.simulator.core.loader.CsvLoader;
import com.alphapowertrading.simulator.core.loader.CsvParser;
import com.alphapowertrading.simulator.core.market.MarketData;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OptimizationRunner {

    private final OptimizationProperties properties;
    private final FleuryOptimizer optimizer;
    private final OptimizationCsvWriter csvWriter;

    public OptimizationRunner(
            OptimizationProperties properties,
            FleuryOptimizer optimizer,
            OptimizationCsvWriter csvWriter) {

        this.properties = properties;
        this.optimizer = optimizer;
        this.csvWriter = csvWriter;
    }

    @PostConstruct
    void checkProperties() {
        System.out.println("========== OPTIMIZER CONFIG ==========");
        System.out.println("symbol       = " + properties.symbol());
        System.out.println("dataDirectory = " + properties.dataDirectory());
        System.out.println("outputFile   = " + properties.outputFile());
    }

    public void run() {

        System.out.println("optimizer.symbol = " + properties.symbol());
        System.out.println("optimizer.dataDirectory = " + properties.dataDirectory());
        System.out.println("optimizer.outputFile = " + properties.outputFile());
        Path file = Path.of(
                properties.dataDirectory(),
                properties.symbol() + ".csv"
        );

        try {
            CsvLoader csvLoader = new CsvParser();

            MarketData marketData = csvLoader.load(file);

            System.out.printf(
                    "Loaded %d candles for %s (%s -> %s)%n",
                    marketData.size(),
                    properties.symbol(),
                    marketData.get(0).date(),
                    marketData.get(marketData.size() - 1).date()
            );

            List<OptimizationResult> results =
                    optimizer.optimize(
                            marketData,
                            properties
                    );

            csvWriter.write(
                    properties.outputFile(),
                    results,
                    properties.sortBy()
            );

            System.out.printf(
                    "Completed %d combinations%n",
                    results.size()
            );

            System.out.println(
                    "Output: " +
                            Path.of(properties.outputFile())
                                    .toAbsolutePath()
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to load market data from: "
                            + file.toAbsolutePath(),
                    e
            );
        }
    }
}