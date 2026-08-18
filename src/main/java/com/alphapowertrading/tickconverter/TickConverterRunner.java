package com.alphapowertrading.tickconverter;

import com.alphapowertrading.tickconverter.config.TickConverterProperties;
import com.alphapowertrading.tickconverter.eurusd.service.EurUsdConverter;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class TickConverterRunner implements CommandLineRunner {

    private final TickConverterProperties properties;
    private final EurUsdConverter converter;

    public TickConverterRunner(
            TickConverterProperties properties,
            EurUsdConverter converter
    ) {
        this.properties = properties;
        this.converter = converter;
    }

    @Override
    public void run(String... args) throws Exception {

        validateProperties();

        Path inputDirectory =
                Path.of(properties.inputDirectory());

        Path outputDirectory =
                Path.of(properties.outputDirectory());

        System.out.println();
        System.out.println("==================================================");
        System.out.println("EURUSD TICK CONVERTER");
        System.out.println("Input:  " + inputDirectory.toAbsolutePath());
        System.out.println("Output: " + outputDirectory.toAbsolutePath());
        System.out.println("==================================================");
        System.out.println();

        List<Integer> frequencies =
                properties.frequencies();

        for (Integer frequency : frequencies) {

            validateFrequency(frequency);

            System.out.println(
                    "--------------------------------------------------"
            );

            System.out.printf(
                    "Starting %s %d-minute conversion%n",
                    properties.instrument(),
                    frequency
            );

            System.out.println(
                    "--------------------------------------------------"
            );

            long start =
                    System.currentTimeMillis();

            converter.convert(
                    inputDirectory,
                    outputDirectory,
                    properties.instrument(),
                    frequency
            );

            double seconds =
                    (System.currentTimeMillis() - start)
                            / 1000.0;

            System.out.printf(
                    "[%dm] Completed in %.1f seconds%n%n",
                    frequency,
                    seconds
            );
        }

        System.out.println(
                "=================================================="
        );

        System.out.println(
                "CONVERSION COMPLETED"
        );

        System.out.println(
                "=================================================="
        );
    }

    private void validateFrequency(Integer frequency) {

        if (frequency == null || frequency <= 0) {
            throw new IllegalArgumentException(
                    "Frequency must be greater than zero"
            );
        }
    }

    private void validateProperties() {

        if (properties.instrument() == null
                || properties.instrument().isBlank()) {

            throw new IllegalArgumentException(
                    "tickconverter.instrument is required"
            );
        }

        if (properties.inputDirectory() == null
                || properties.inputDirectory().isBlank()) {

            throw new IllegalArgumentException(
                    "tickconverter.input-directory is required"
            );
        }

        if (properties.outputDirectory() == null
                || properties.outputDirectory().isBlank()) {

            throw new IllegalArgumentException(
                    "tickconverter.output-directory is required"
            );
        }

        if (properties.frequencies() == null
                || properties.frequencies().isEmpty()) {

            throw new IllegalArgumentException(
                    "tickconverter.frequencies is required"
            );
        }
    }
}