package com.alphapowertrading.simulator.core.loader;

import com.alphapowertrading.simulator.core.market.MarketData;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class CsvLoaderDispatcher implements CsvLoader {

    private final List<MarketDataCsvLoader> loaders;

    public CsvLoaderDispatcher(List<MarketDataCsvLoader> loaders) {
        this.loaders = loaders;
    }

    @Override
    public MarketData load(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("CSV file not found: " + file.toAbsolutePath());
        }

        String header = readHeader(file);

        return loaders.stream()
                .filter(loader -> loader.supports(header))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported CSV format. Header: " + header))
                .load(file);
    }

    private String readHeader(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(
                file, StandardCharsets.UTF_8)) {
            String header = reader.readLine();

            if (header == null || header.isBlank()) {
                throw new IllegalArgumentException(
                        "CSV file has no header: " + file.toAbsolutePath());
            }

            return header;
        }
    }
}
