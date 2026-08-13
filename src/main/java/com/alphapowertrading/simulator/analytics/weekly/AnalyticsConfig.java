package com.alphapowertrading.simulator.analytics.weekly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record AnalyticsConfig(
        String dataDirectory,
        String symbol
) {

    private static final String DEFAULT_YAML =
            "src/main/resources/application.yml";

    public static AnalyticsConfig load(
            String[] args
    ) throws IOException {

        Path yaml =
                args.length > 0
                        ? Path.of(args[0])
                        : Path.of(DEFAULT_YAML);

        if (!Files.exists(yaml)) {
            throw new IOException(
                    "YAML file not found: "
                            + yaml.toAbsolutePath()
            );
        }

        String content =
                Files.readString(yaml);

        String dataDirectory =
                find(
                        content,
                        "data-directory"
                );

        if (dataDirectory == null) {
            dataDirectory =
                    find(
                            content,
                            "dataDirectory"
                    );
        }

        String symbol =
                find(
                        content,
                        "symbol"
                );

        if (dataDirectory == null
                || dataDirectory.isBlank()) {
            dataDirectory = "data";
        }

        if (symbol == null
                || symbol.isBlank()) {
            throw new IllegalArgumentException(
                    "simulator.symbol not found in "
                            + yaml
            );
        }

        return new AnalyticsConfig(
                dataDirectory,
                symbol
        );
    }

    private static String find(
            String content,
            String key
    ) {
        Pattern pattern =
                Pattern.compile(
                        "(?m)^\\s*"
                                + Pattern.quote(key)
                                + "\\s*:\\s*[\"']?([^\"'\\s#]+)"
                );

        Matcher matcher =
                pattern.matcher(content);

        return matcher.find()
                ? matcher.group(1)
                : null;
    }
}
