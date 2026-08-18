package com.alphapowertrading.simulator.analytics.weekly;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public final class YamlConfigLoader {

    private YamlConfigLoader() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> load(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IllegalArgumentException(
                    "YAML configuration not found: "
                            + file.toAbsolutePath()
            );
        }

        try (InputStream input = Files.newInputStream(file)) {
            Object loaded = new Yaml().load(input);

            if (!(loaded instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(
                        "Invalid YAML configuration: " + file
                );
            }

            return (Map<String, Object>) map;
        }
    }

    public static String getString(
            Map<String, Object> root,
            String section,
            String... keys
    ) {
        Object sectionValue = root.get(section);

        if (!(sectionValue instanceof Map<?, ?> sectionMap)) {
            return null;
        }

        for (String key : keys) {
            Object value = sectionMap.get(key);

            if (value != null) {
                return String.valueOf(value);
            }
        }

        return null;
    }
}
