package com.alphapowertrading.simulator.core.loader;

import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class TradingViewCsvLoader implements MarketDataCsvLoader {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Override
    public boolean supports(String header) {
        String normalized = normalizeHeader(header);

        return normalized.contains("fecha")
                && normalized.contains("ultimo")
                && normalized.contains("apertura")
                && normalized.contains("maximo")
                && normalized.contains("minimo");
    }

    @Override
    public MarketData load(Path file) throws IOException {
        List<Candle> candles = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(
                file, StandardCharsets.UTF_8)) {

            String line;
            int lineNumber = 0;
            String fileName = file.getFileName().toString();

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.isBlank() || lineNumber == 1) {
                    continue;
                }

                try {
                    String[] fields = parseCsvLine(line);

                    if (fields.length < 5) {
                        throw new IllegalArgumentException(
                                "Expected at least 5 columns, found " + fields.length);
                    }

                    LocalDate date = parseDate(fields[0]);
                    long close = parsePrice(fields[1]);
                    long open = parsePrice(fields[2]);
                    long high = parsePrice(fields[3]);
                    long low = parsePrice(fields[4]);

                    if (fileName.equalsIgnoreCase("3QQQ.CSV")
                            && date.isBefore(LocalDate.of(2020, Month.NOVEMBER, 9))) {
                        close /= 33;
                        open /= 33;
                        high /= 33;
                        low /= 33;
                    }

                    candles.add(new Candle(
                            date.atStartOfDay(), open, high, low, close));

                } catch (RuntimeException e) {
                    throw new IllegalArgumentException(
                            "Invalid CSV data at line " + lineNumber + ": " + line, e);
                }
            }
        }

        candles.sort(Comparator.comparing(Candle::date));
        validateChronology(candles);
        return new MarketData(candles);
    }

    private static long parsePrice(String value) {
        String normalized = unquote(value).trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Empty price");
        }

        int commaIndex = normalized.indexOf(',');

        if (commaIndex >= 0) {
            String integerPart = normalized.substring(0, commaIndex).replace(".", "");
            String decimalPart = normalized.substring(commaIndex + 1);

            if (decimalPart.length() == 1) {
                decimalPart += "0";
            }

            if (decimalPart.length() != 2) {
                throw new IllegalArgumentException(
                        "Price must contain one or two decimal digits: " + normalized);
            }

            return Long.parseLong(integerPart + decimalPart);
        }

        return Long.parseLong(normalized.replace(".", "")) * 100;
    }

    private static LocalDate parseDate(String value) {
        String normalized = unquote(value).trim();

        try {
            return LocalDate.parse(normalized, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date: " + normalized, e);
        }
    }

    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);

            if (character == '"') {
                quoted = !quoted;
            } else if (character == ',' && !quoted) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }

        fields.add(current.toString());
        return fields.toArray(String[]::new);
    }

    private static String normalizeHeader(String header) {
        return unquote(header)
                .trim()
                .toLowerCase()
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace(" ", "");
    }

    private static String unquote(String value) {
        String result = value.trim();

        if (result.length() >= 2
                && result.startsWith("\"")
                && result.endsWith("\"")) {
            return result.substring(1, result.length() - 1);
        }

        return result;
    }

    private static void validateChronology(List<Candle> candles) {
        for (int i = 1; i < candles.size(); i++) {
            if (candles.get(i).date().isBefore(candles.get(i - 1).date())) {
                throw new IllegalArgumentException("Candles are not chronological");
            }
        }
    }
}
