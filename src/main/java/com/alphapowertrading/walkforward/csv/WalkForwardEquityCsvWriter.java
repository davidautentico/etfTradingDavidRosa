package com.alphapowertrading.walkforward.csv;

import com.alphapowertrading.walkforward.model.EquityPoint;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class WalkForwardEquityCsvWriter {

    public void write(String outputFile, List<EquityPoint> points) throws IOException {
        Path path = Path.of(outputFile);

        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("date,equity");
            writer.newLine();

            for (EquityPoint point : points) {
                writer.write(point.date() + "," + point.equity());
                writer.newLine();
            }
        }
    }
}
