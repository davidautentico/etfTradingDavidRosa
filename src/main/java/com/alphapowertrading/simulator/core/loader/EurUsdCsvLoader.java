package com.alphapowertrading.simulator.core.loader;

import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class EurUsdCsvLoader implements MarketDataCsvLoader {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public boolean supports(String header) {
        return header.trim().toLowerCase()
                .equals("timestamp,open,high,low,close");
    }

    @Override
    public MarketData load(Path file) throws IOException {
        List<Candle> candles = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(
                file, StandardCharsets.UTF_8)) {

            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.isBlank() || lineNumber == 1) {
                    continue;
                }

                try {
                    String[] fields = line.split(",", -1);

                    if (fields.length != 5) {
                        throw new IllegalArgumentException(
                                "Expected 5 columns, found " + fields.length);
                    }

                    LocalDateTime timestamp = LocalDateTime.parse(
                            fields[0].trim(), TIMESTAMP_FORMAT);
                    long open = Long.parseLong(fields[1].trim());
                    long high = Long.parseLong(fields[2].trim());
                    long low = Long.parseLong(fields[3].trim());
                    long close = Long.parseLong(fields[4].trim());

                    candles.add(new Candle(
                            timestamp, open, high, low, close));

                } catch (RuntimeException e) {
                    throw new IllegalArgumentException(
                            "Invalid EUR/USD CSV data at line "
                                    + lineNumber + ": " + line, e);
                }
            }
        }

        candles.sort(Comparator.comparing(Candle::date));
        validateChronology(candles);
        return new MarketData(candles);
    }

    private static void validateChronology(List<Candle> candles) {
        for (int i = 1; i < candles.size(); i++) {
            if (candles.get(i).date().isBefore(candles.get(i - 1).date())) {
                throw new IllegalArgumentException("Candles are not chronological");
            }
        }
    }
}
