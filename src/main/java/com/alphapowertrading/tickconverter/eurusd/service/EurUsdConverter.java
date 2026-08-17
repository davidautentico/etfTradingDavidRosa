package com.alphapowertrading.tickconverter.eurusd.service;

import com.alphapowertrading.tickconverter.core.aggregation.BidAskAggregator;
import com.alphapowertrading.tickconverter.core.model.Ohlc;
import com.alphapowertrading.tickconverter.csv.BidAskCsvWriter;
import com.alphapowertrading.tickconverter.csv.SpreadHourlyCsvWriter;
import com.alphapowertrading.tickconverter.eurusd.loader.EurUsdTickLoader;
import com.alphapowertrading.tickconverter.eurusd.model.EurUsdTickFile;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class EurUsdConverter {

    private static final DateTimeFormatter CSV_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EurUsdTickLoader loader;
    private final BidAskCsvWriter writer;
    private final SpreadHourlyCsvWriter spreadWriter;

    public EurUsdConverter(
            EurUsdTickLoader loader,
            BidAskCsvWriter writer,
            SpreadHourlyCsvWriter spreadWriter) {
        this.loader = loader;
        this.writer = writer;
        this.spreadWriter = spreadWriter;
    }

    public void convert(
            Path inputDirectory,
            Path outputDirectory,
            String instrument,
            int frequency)
            throws IOException {

        Files.createDirectories(outputDirectory);

        List<EurUsdTickFile> files =
                findFiles(inputDirectory, instrument);

        List<EurUsdTickFile> bidFiles =
                files.stream()
                        .filter(file -> file.side() == EurUsdTickFile.Side.BID)
                        .toList();

        List<EurUsdTickFile> askFiles =
                files.stream()
                        .filter(file -> file.side() == EurUsdTickFile.Side.ASK)
                        .toList();

        if (bidFiles.isEmpty()) {
            throw new IllegalStateException(
                    "No BID .log files found in "
                            + inputDirectory.toAbsolutePath());
        }

        if (askFiles.isEmpty()) {
            throw new IllegalStateException(
                    "No ASK .log files found in "
                            + inputDirectory.toAbsolutePath());
        }

        Path oneMinuteFile =
                outputDirectory.resolve(instrument + "_1m.csv");

        Path fiveMinuteFile =
                outputDirectory.resolve(instrument + "_5m.csv");

        Path fifteenMinuteFile =
                outputDirectory.resolve(instrument + "_15m.csv");

        Path spreadFile =
                outputDirectory.resolve(instrument + "_spread_hourly.csv");

        Files.deleteIfExists(oneMinuteFile);
        Files.deleteIfExists(fiveMinuteFile);
        Files.deleteIfExists(fifteenMinuteFile);
        Files.deleteIfExists(spreadFile);

        Map<SpreadHourlyCsvWriter.SpreadKey,
                SpreadHourlyCsvWriter.SpreadStats> spreadStatistics =
                new HashMap<>();

        System.out.println();
        System.out.println("==================================================");
        System.out.println("GENERATING 1 MINUTE FROM BID");
        System.out.println("==================================================");

        generateOneMinute(
                bidFiles,
                oneMinuteFile);

        System.out.println();
        System.out.println("==================================================");
        System.out.println("GENERATING 5 MINUTE FROM 1 MINUTE");
        System.out.println("==================================================");

        aggregate(
                oneMinuteFile,
                fiveMinuteFile,
                5);

        System.out.println();
        System.out.println("==================================================");
        System.out.println("GENERATING 15 MINUTE FROM 5 MINUTE");
        System.out.println("==================================================");

        aggregate(
                fiveMinuteFile,
                fifteenMinuteFile,
                15);

        System.out.println();
        System.out.println("==================================================");
        System.out.println("CALCULATING HOURLY SPREAD");
        System.out.println("==================================================");

        calculateSpread(
                bidFiles,
                askFiles,
                spreadStatistics);

        spreadWriter.write(
                spreadFile,
                spreadStatistics);

        System.out.println();
        System.out.println("==================================================");
        System.out.println("CONVERSION COMPLETED");
        System.out.println("==================================================");

        System.out.println(
                "1m:     " + oneMinuteFile.toAbsolutePath());

        System.out.println(
                "5m:     " + fiveMinuteFile.toAbsolutePath());

        System.out.println(
                "15m:    " + fifteenMinuteFile.toAbsolutePath());

        System.out.println(
                "Spread: " + spreadFile.toAbsolutePath());
    }

    private void generateOneMinute(
            List<EurUsdTickFile> bidFiles,
            Path outputFile)
            throws IOException {

        BidAskAggregator aggregator =
                new BidAskAggregator(1);

        try (BufferedWriter output = writer.open(outputFile)) {
            for (int i = 0; i < bidFiles.size(); i++) {
                EurUsdTickFile file = bidFiles.get(i);

                EurUsdTickLoader.FileStats stats =
                        loader.load(
                                file,
                                aggregator::add);

                writeCompletedCandles(
                        output,
                        aggregator);

                double progress =
                        (i + 1) * 100.0 / bidFiles.size();

                System.out.printf(
                        "[1m] BID %d/%d (%.1f%%) %s | "
                                + "rows: %,d | valid: %,d | invalid: %,d%n",
                        i + 1,
                        bidFiles.size(),
                        progress,
                        file.path().getFileName(),
                        stats.rows(),
                        stats.validRows(),
                        stats.invalidRows());
            }

            writeCompletedCandles(
                    output,
                    aggregator);

            Ohlc last =
                    aggregator.flush();

            if (last != null) {
                writer.write(
                        output,
                        last);
            }
        }
    }

    private void aggregate(
            Path inputFile,
            Path outputFile,
            int frequency)
            throws IOException {

        OhlcTimeframeAggregator aggregator =
                new OhlcTimeframeAggregator(frequency);

        try (
                BufferedReader input =
                        Files.newBufferedReader(
                                inputFile,
                                StandardCharsets.UTF_8);
                BufferedWriter output =
                        writer.open(outputFile)) {

            input.readLine();

            String line;

            while ((line = input.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                Ohlc candle =
                        parseOhlc(line);

                List<Ohlc> completed =
                        aggregator.add(candle);

                for (Ohlc completedCandle : completed) {
                    writer.write(
                            output,
                            completedCandle);
                }
            }

            List<Ohlc> completed =
                    aggregator.flush();

            for (Ohlc completedCandle : completed) {
                writer.write(
                        output,
                        completedCandle);
            }
        }
    }

    /**
     * Calculates the spread using the latest known BID and ASK.
     *
     * <p>The spread is stored directly in minipips. With the six-digit integer
     * price representation used by EURUSD, one unit corresponds to one
     * minipip:
     *
     * <pre>
     * 1 pip     = 0.00010 = 10 minipips
     * 1 minipip = 0.00001
     * </pre>
     */
    private void calculateSpread(
            List<EurUsdTickFile> bidFiles,
            List<EurUsdTickFile> askFiles,
            Map<SpreadHourlyCsvWriter.SpreadKey,
                    SpreadHourlyCsvWriter.SpreadStats> statistics)
            throws IOException {

        List<EurUsdTickFile> allFiles =
                new ArrayList<>(
                        bidFiles.size() + askFiles.size());

        allFiles.addAll(bidFiles);
        allFiles.addAll(askFiles);

        allFiles.sort(
                Comparator
                        .comparing(EurUsdTickFile::date)
                        .thenComparingInt(EurUsdTickFile::hour)
                        .thenComparing(
                                file -> file.side().name())
                        .thenComparing(
                                file -> file.path().toString()));

        SpreadState state =
                new SpreadState();

        for (int i = 0; i < allFiles.size(); i++) {
            EurUsdTickFile file =
                    allFiles.get(i);

            loader.load(
                    file,
                    tick -> {
                        if (file.side() == EurUsdTickFile.Side.BID) {
                            state.bid = tick.price();
                        } else {
                            state.ask = tick.price();
                        }

                        if (state.bid != null
                                && state.ask != null) {

                            int spreadMinipips =
                                    Math.abs(
                                            state.bid - state.ask);

                            addSpread(
                                    statistics,
                                    tick.timestamp(),
                                    spreadMinipips);
                        }
                    });

            double progress =
                    (i + 1) * 100.0 / allFiles.size();

            System.out.printf(
                    "[SPREAD] %d/%d (%.1f%%) %s%n",
                    i + 1,
                    allFiles.size(),
                    progress,
                    file.path().getFileName());
        }
    }

    private void addSpread(
            Map<SpreadHourlyCsvWriter.SpreadKey,
                    SpreadHourlyCsvWriter.SpreadStats> statistics,
            Instant timestamp,
            int spreadMinipips) {

        var dateTime =
                timestamp.atZone(ZoneOffset.UTC);

        var key =
                new SpreadHourlyCsvWriter.SpreadKey(
                        dateTime.getYear(),
                        dateTime.getHour());

        var current =
                statistics.getOrDefault(
                        key,
                        new SpreadHourlyCsvWriter.SpreadStats(
                                0,
                                0));

        statistics.put(
                key,
                new SpreadHourlyCsvWriter.SpreadStats(
                        current.totalSpread()
                                + spreadMinipips,
                        current.count() + 1));
    }

    private Ohlc parseOhlc(String line) {
        String[] fields =
                line.split(",");

        if (fields.length != 5) {
            throw new IllegalArgumentException(
                    "Invalid OHLC line: " + line);
        }

        Instant timestamp =
                LocalDateTime.parse(
                                fields[0].trim(),
                                CSV_TIMESTAMP_FORMATTER)
                        .toInstant(ZoneOffset.UTC);

        return new Ohlc(
                timestamp,
                Integer.parseInt(fields[1].trim()),
                Integer.parseInt(fields[2].trim()),
                Integer.parseInt(fields[3].trim()),
                Integer.parseInt(fields[4].trim()));
    }

    private void writeCompletedCandles(
            BufferedWriter output,
            BidAskAggregator aggregator)
            throws IOException {

        Ohlc candle;

        while ((candle =
                aggregator.pollCompleted()) != null) {
            writer.write(
                    output,
                    candle);
        }
    }

    private List<EurUsdTickFile> findFiles(
            Path directory,
            String instrument)
            throws IOException {

        String expectedInstrument =
                instrument.toUpperCase(Locale.ROOT);

        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(
                            path ->
                                    path.getFileName()
                                            .toString()
                                            .toLowerCase(Locale.ROOT)
                                            .endsWith(".log"))
                    .map(loader::parse)
                    .filter(
                            file ->
                                    file.symbol()
                                            .equalsIgnoreCase(
                                                    expectedInstrument))
                    .sorted(
                            Comparator
                                    .comparing(
                                            EurUsdTickFile::date)
                                    .thenComparingInt(
                                            EurUsdTickFile::hour)
                                    .thenComparing(
                                            file ->
                                                    file.side().name()))
                    .toList();
        }
    }

    private static final class SpreadState {

        private Integer bid;
        private Integer ask;
    }

    private static final class OhlcTimeframeAggregator {

        private final long intervalSeconds;

        private Instant currentBucket;
        private int open;
        private int high;
        private int low;
        private int close;
        private boolean initialized;

        private OhlcTimeframeAggregator(int minutes) {
            if (minutes <= 0) {
                throw new IllegalArgumentException(
                        "Frequency must be greater than zero");
            }

            intervalSeconds =
                    minutes * 60L;
        }

        private List<Ohlc> add(Ohlc candle) {
            List<Ohlc> completed =
                    new ArrayList<>();

            Instant bucket =
                    bucketStart(
                            candle.timestamp());

            if (!initialized) {
                start(
                        bucket,
                        candle);

                return completed;
            }

            if (bucket.equals(currentBucket)) {
                high =
                        Math.max(
                                high,
                                candle.high());

                low =
                        Math.min(
                                low,
                                candle.low());

                close =
                        candle.close();

                return completed;
            }

            completed.add(
                    create());

            Instant nextBucket =
                    currentBucket.plusSeconds(
                            intervalSeconds);

            while (nextBucket.isBefore(bucket)) {
                completed.add(
                        new Ohlc(
                                nextBucket,
                                close,
                                close,
                                close,
                                close));

                nextBucket =
                        nextBucket.plusSeconds(
                                intervalSeconds);
            }

            start(
                    bucket,
                    candle);

            return completed;
        }

        private List<Ohlc> flush() {
            List<Ohlc> completed =
                    new ArrayList<>();

            if (!initialized) {
                return completed;
            }

            completed.add(
                    create());

            initialized = false;
            currentBucket = null;

            return completed;
        }

        private void start(
                Instant bucket,
                Ohlc candle) {

            currentBucket = bucket;

            open = candle.open();
            high = candle.high();
            low = candle.low();
            close = candle.close();

            initialized = true;
        }

        private Ohlc create() {
            return new Ohlc(
                    currentBucket,
                    open,
                    high,
                    low,
                    close);
        }

        private Instant bucketStart(
                Instant timestamp) {

            long epochSeconds =
                    timestamp.getEpochSecond();

            long bucketSeconds =
                    Math.floorDiv(
                            epochSeconds,
                            intervalSeconds)
                            * intervalSeconds;

            return Instant.ofEpochSecond(
                    bucketSeconds);
        }
    }
}