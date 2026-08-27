package com.alphapowertrading.synthetic.service;

import com.alphapowertrading.simulator.core.loader.MarketDataCsvLoader;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.synthetic.config.SyntheticSeriesProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SyntheticSeriesRunner
        implements CommandLineRunner {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SyntheticSeriesProperties properties;
    private final SyntheticMarketDataConverter converter;
    private final List<MarketDataCsvLoader> csvLoaders;

    public SyntheticSeriesRunner(
            SyntheticSeriesProperties properties,
            SyntheticMarketDataConverter converter,
            List<MarketDataCsvLoader> csvLoaders) {

        this.properties = properties;
        this.converter = converter;
        this.csvLoaders = csvLoaders;
    }

    @Override
    public void run(String... args)
            throws Exception {

        validateConfiguration();

        Path dataDirectory =
                Path.of(
                        properties.getDataDirectory());

        Path inputFile =
                dataDirectory.resolve(
                        properties.getBaseSymbol()
                                + ".csv");

        Path outputFile =
                dataDirectory.resolve(
                        buildOutputFileName());

        System.out.println();
        System.out.println(
                "========================================");
        System.out.println(
                "      Synthetic Series Generator");
        System.out.println(
                "========================================");
        System.out.println(
                "Base symbol : "
                        + properties.getBaseSymbol());
        System.out.println(
                "Leverage    : "
                        + formatLeverage(
                        properties.getLeverage()));
        System.out.println(
                "Input       : "
                        + inputFile.toAbsolutePath());
        System.out.println(
                "Output      : "
                        + outputFile.toAbsolutePath());
        System.out.println(
                "========================================");

        if (!Files.exists(inputFile)) {
            throw new IllegalArgumentException(
                    "Base market data file does not exist: "
                            + inputFile.toAbsolutePath());
        }

        String header =
                readHeader(inputFile);

        MarketData baseData =
                loadMarketData(
                        inputFile,
                        header);

        if (baseData == null
                || baseData.size() == 0) {

            throw new IllegalArgumentException(
                    "Base market data is empty: "
                            + inputFile.toAbsolutePath());
        }

        System.out.printf(
                "Loaded %d candles (%s -> %s)%n",
                baseData.size(),
                baseData.get(0).date(),
                baseData.get(
                                baseData.size() - 1)
                        .date());

        /*
         * Conversion is ALWAYS performed from
         * oldest -> newest.
         */
        MarketData syntheticData =
                converter.convert(
                        baseData,
                        properties.getLeverage());

        /*
         * CSV output is ALWAYS written from
         * newest -> oldest.
         */
        writeMarketData(
                outputFile,
                header,
                syntheticData);

        System.out.println();
        System.out.println(
                "Synthetic series generated successfully.");
        System.out.println(
                "Candles: "
                        + syntheticData.size());
        System.out.println(
                "File: "
                        + outputFile.toAbsolutePath());
        System.out.println();
    }

    private String readHeader(
            Path inputFile)
            throws Exception {

        try (var reader =
                     Files.newBufferedReader(
                             inputFile,
                             StandardCharsets.UTF_8)) {

            String header =
                    reader.readLine();

            if (header == null
                    || header.isBlank()) {

                throw new IllegalArgumentException(
                        "CSV file has no header: "
                                + inputFile.toAbsolutePath());
            }

            return header;
        }
    }

    private MarketData loadMarketData(
            Path inputFile,
            String header)
            throws Exception {

        for (MarketDataCsvLoader loader :
                csvLoaders) {

            if (loader.supports(header)) {

                System.out.println(
                        "CSV loader: "
                                + loader.getClass()
                                .getSimpleName());

                return loader.load(inputFile);
            }
        }

        throw new IllegalArgumentException(
                "No MarketDataCsvLoader supports "
                        + "CSV header: "
                        + header);
    }

    private void writeMarketData(
            Path outputFile,
            String header,
            MarketData marketData)
            throws Exception {

        Files.createDirectories(
                outputFile.getParent());

        /*
         * Copy the candles so that the MarketData
         * object itself is not modified.
         */
        List<Candle> candles =
                new ArrayList<>(
                        marketData.candles());

        /*
         * NEWEST -> OLDEST
         *
         * The calculation has already been done
         * chronologically. We only reverse the
         * order for the resulting CSV.
         */
        candles.sort(
                (first, second) ->
                        second.date().compareTo(first.date()));

        StringBuilder content =
                new StringBuilder();

        /*
         * EXACTLY the same header as the base.
         */
        content
                .append(header)
                .append(System.lineSeparator());

        for (Candle candle : candles) {

            content
                    .append(
                            formatDate(candle))
                    .append(",")
                    .append(
                            quote(
                                    formatPrice(
                                            candle.close())))
                    .append(",")
                    .append(
                            quote(
                                    formatPrice(
                                            candle.open())))
                    .append(",")
                    .append(
                            quote(
                                    formatPrice(
                                            candle.high())))
                    .append(",")
                    .append(
                            quote(
                                    formatPrice(
                                            candle.low())))
                    .append(System.lineSeparator());
        }

        Files.writeString(
                outputFile,
                content.toString(),
                StandardCharsets.UTF_8);
    }

    private String formatDate(
            Candle candle) {

        return candle.date()
                .format(DATE_FORMAT);
    }

    private String formatPrice(
            long value) {

        long integerPart =
                value / 100;

        long decimalPart =
                Math.abs(value % 100);

        return String.format(
                Locale.US,
                "%d,%02d",
                integerPart,
                decimalPart);
    }

    private String quote(
            String value) {

        return "\"" + value + "\"";
    }

    private String buildOutputFileName() {

        return properties.getBaseSymbol()
                + "_syn_"
                + formatLeverage(
                properties.getLeverage())
                + ".csv";
    }

    private String formatLeverage(
            double leverage) {

        return String.format(
                Locale.US,
                "%.1f",
                leverage);
    }

    private void validateConfiguration() {

        if (properties.getBaseSymbol() == null
                || properties.getBaseSymbol().isBlank()) {

            throw new IllegalArgumentException(
                    "synthetic.base-symbol "
                            + "must be configured");
        }

        if (properties.getDataDirectory() == null
                || properties.getDataDirectory().isBlank()) {

            throw new IllegalArgumentException(
                    "synthetic.data-directory "
                            + "must be configured");
        }

        double leverage =
                properties.getLeverage();

        if (leverage < 1.1
                || leverage > 5.0) {

            throw new IllegalArgumentException(
                    "synthetic.leverage must be "
                            + "between 1.1 and 5.0");
        }

        double rounded =
                Math.round(leverage * 10.0)
                        / 10.0;

        if (Math.abs(
                leverage - rounded)
                > 0.000001) {

            throw new IllegalArgumentException(
                    "synthetic.leverage must use "
                            + "increments of 0.1");
        }
    }
}