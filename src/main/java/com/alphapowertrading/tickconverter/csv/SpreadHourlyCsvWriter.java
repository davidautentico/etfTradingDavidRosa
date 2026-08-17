package com.alphapowertrading.tickconverter.csv;

import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class SpreadHourlyCsvWriter {

    public void write(
            Path file,
            Map<SpreadKey, SpreadStats> statistics)
            throws IOException {

        Path parent = file.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer =
                     Files.newBufferedWriter(
                             file,
                             StandardCharsets.UTF_8)) {

            writer.write("year,hour,avg_spread");
            writer.newLine();

            statistics.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> writeEntry(writer, entry));
        }
    }

    private void writeEntry(
            BufferedWriter writer,
            Map.Entry<SpreadKey, SpreadStats> entry) {

        try {
            SpreadStats stats = entry.getValue();

            long averageSpread =
                    stats.count() == 0
                            ? 0
                            : stats.totalSpread() / stats.count();

            writer.write(
                    entry.getKey().year()
                            + ","
                            + entry.getKey().hour()
                            + ","
                            + averageSpread);

            writer.newLine();
        } catch (IOException e) {
            throw new SpreadWriteException(e);
        }
    }

    public record SpreadKey(
            int year,
            int hour)
            implements Comparable<SpreadKey> {

        @Override
        public int compareTo(SpreadKey other) {
            int yearComparison =
                    Integer.compare(year, other.year());

            if (yearComparison != 0) {
                return yearComparison;
            }

            return Integer.compare(hour, other.hour());
        }
    }

    public record SpreadStats(
            long totalSpread,
            long count) {}

    private static class SpreadWriteException
            extends RuntimeException {

        private SpreadWriteException(IOException cause) {
            super(cause);
        }
    }
}