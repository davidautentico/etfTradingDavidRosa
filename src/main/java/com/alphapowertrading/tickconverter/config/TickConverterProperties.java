package com.alphapowertrading.tickconverter.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tickconverter")
public record TickConverterProperties(
        String instrument,
        String inputDirectory,
        String outputDirectory,
        String timezone,
        List<Integer> frequencies
) {
}
