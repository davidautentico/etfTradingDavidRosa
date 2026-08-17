package com.alphapowertrading.tickconverter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "tickconverter")
public record TickConverterProperties(
        String instrument,
        String inputDirectory,
        String outputDirectory,
        String timezone,
        List<Integer> frequencies
) {
}
