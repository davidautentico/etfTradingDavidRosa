package com.alphapowertrading.simulator.analytics.weekly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads simulator configuration directly from application.yml.
 */
public record AnalyticsConfigDirect(
    String dataDirectory,
    String symbol) {

  private static final Path DEFAULT_YAML =
      Path.of("src/main/resources/application.yml");

  public static AnalyticsConfigDirect load()
      throws IOException {

    return load(DEFAULT_YAML);
  }

  public static AnalyticsConfigDirect load(
      Path yaml)
      throws IOException {

    if (!Files.exists(yaml)) {
      throw new IOException(
          "YAML file not found: "
              + yaml.toAbsolutePath());
    }

    String content =
        Files.readString(yaml);

    String dataDirectory =
        find(content, "data-directory");

    if (dataDirectory == null) {
      dataDirectory =
          find(content, "dataDirectory");
    }

    String symbol =
        find(content, "symbol");

    if (dataDirectory == null
        || dataDirectory.isBlank()) {
      dataDirectory = "data";
    }

    if (symbol == null
        || symbol.isBlank()) {
      throw new IllegalArgumentException(
          "simulator.symbol not found in "
              + yaml.toAbsolutePath());
    }

    return new AnalyticsConfigDirect(
        dataDirectory,
        symbol);
  }

  private static String find(
      String content,
      String key) {

    Pattern pattern =
        Pattern.compile(
            "(?m)^\\s*"
                + Pattern.quote(key)
                + "\\s*:\\s*[\"']?"
                + "([^\"'\\s#]+)");

    Matcher matcher =
        pattern.matcher(content);

    return matcher.find()
        ? matcher.group(1)
        : null;
  }
}
