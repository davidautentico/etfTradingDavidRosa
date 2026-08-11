package com.alphapowertrading.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
        String symbol,
        String dataDirectory,
        double initialCapital,
        String strategyName
) {
    public SimulatorProperties {
        symbol = symbol == null || symbol.isBlank() ? "3QQQ" : symbol;
        dataDirectory = dataDirectory == null || dataDirectory.isBlank() ? "data" : dataDirectory;
        if (initialCapital <= 0) {
            initialCapital = 100_000.0;
        }
    }
}
