package com.alphapowertrading.tickconverter.eurusd.loader;

import com.alphapowertrading.tickconverter.core.model.Tick;
import com.alphapowertrading.tickconverter.eurusd.model.EurUsdTickFile;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class EurUsdTickLoader {

    private static final int PRICE_SCALE = 100_000;

    private final EurUsdTickFileNameParser filenameParser;

    public EurUsdTickLoader(EurUsdTickFileNameParser filenameParser) {
        this.filenameParser = filenameParser;
    }

    public EurUsdTickFile parse(java.nio.file.Path file) {
        return filenameParser.parse(file);
    }

    public FileStats load(
            EurUsdTickFile file,
            Consumer<Tick> consumer)
            throws IOException {

        long rows = 0;
        long validRows = 0;
        long invalidRows = 0;

        try (BufferedReader reader =
                     Files.newBufferedReader(
                             file.path(),
                             StandardCharsets.UTF_8)) {

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String lower = line.toLowerCase();

                if (lower.startsWith("timestamp,")
                        || lower.startsWith("index,")) {
                    continue;
                }

                rows++;

                try {
                    Tick tick = parseTick(line);

                    if (tick == null) {
                        invalidRows++;
                        continue;
                    }

                    consumer.accept(tick);
                    validRows++;
                } catch (NumberFormatException e) {
                    invalidRows++;
                }
            }
        }

        return new FileStats(
                rows,
                validRows,
                invalidRows);
    }

    /**
     * Loads BID and ASK files simultaneously.
     *
     * <p>Only ticks with the same timestamp are passed to the consumer.
     * Processing is streaming and therefore does not require loading the
     * complete files into memory.
     */
    public PairedFileStats loadPaired(
            EurUsdTickFile bidFile,
            EurUsdTickFile askFile,
            BiConsumer<Tick, Tick> consumer)
            throws IOException {

        long bidRows = 0;
        long askRows = 0;
        long matchedRows = 0;
        long unmatchedRows = 0;

        try (
                BufferedReader bidReader =
                        Files.newBufferedReader(
                                bidFile.path(),
                                StandardCharsets.UTF_8);
                BufferedReader askReader =
                        Files.newBufferedReader(
                                askFile.path(),
                                StandardCharsets.UTF_8)) {

            Tick bid = readNextTick(bidReader);
            Tick ask = readNextTick(askReader);

            while (bid != null && ask != null) {
                if (bid.timestamp().equals(ask.timestamp())) {
                    consumer.accept(bid, ask);

                    matchedRows++;

                    bid = readNextTick(bidReader);
                    ask = readNextTick(askReader);

                    continue;
                }

                if (bid.timestamp().isBefore(ask.timestamp())) {
                    unmatchedRows++;
                    bid = readNextTick(bidReader);
                } else {
                    unmatchedRows++;
                    ask = readNextTick(askReader);
                }
            }

            while (bid != null) {
                unmatchedRows++;
                bid = readNextTick(bidReader);
            }

            while (ask != null) {
                unmatchedRows++;
                ask = readNextTick(askReader);
            }
        }

        return new PairedFileStats(
                bidRows,
                askRows,
                matchedRows,
                unmatchedRows);
    }

    private Tick readNextTick(BufferedReader reader)
            throws IOException {

        String line;

        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }

            String lower = line.toLowerCase();

            if (lower.startsWith("timestamp,")
                    || lower.startsWith("index,")) {
                continue;
            }

            try {
                Tick tick = parseTick(line);

                if (tick != null) {
                    return tick;
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed rows.
            }
        }

        return null;
    }

    private Tick parseTick(String line) {
        String[] fields = line.split(",");

        long timestamp;
        String priceValue;
        String volumeValue;

        if (fields.length == 3) {
            timestamp = Long.parseLong(fields[0].trim());
            priceValue = fields[1].trim();
            volumeValue = fields[2].trim();
        } else if (fields.length >= 4) {
            timestamp = Long.parseLong(fields[1].trim());
            priceValue = fields[2].trim();
            volumeValue = fields[3].trim();
        } else {
            return null;
        }

        int price = parsePrice(priceValue);
        double volume = Double.parseDouble(volumeValue);

        return new Tick(
                Instant.ofEpochMilli(timestamp),
                price,
                volume);
    }

    private int parsePrice(String value) {
        return (int) Math.round(
                Double.parseDouble(value) * PRICE_SCALE);
    }

    public record FileStats(
            long rows,
            long validRows,
            long invalidRows) {}

    public record PairedFileStats(
            long bidRows,
            long askRows,
            long matchedRows,
            long unmatchedRows) {}
}