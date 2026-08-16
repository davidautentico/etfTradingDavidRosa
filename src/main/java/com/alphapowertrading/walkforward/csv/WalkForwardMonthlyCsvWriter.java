package com.alphapowertrading.walkforward.csv;

import com.alphapowertrading.walkforward.model.WalkForwardMonthlyResult;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class WalkForwardMonthlyCsvWriter {

    public void write(String outputFile, List<WalkForwardMonthlyResult> results)
            throws IOException {

        Path path = Path.of(outputFile);

        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(
                    "window,isStart,isEnd,oosStart,oosEnd,tp,tph,sl,"
                            + "initialEquity,finalEquity,returnPct,trades,winPercentage"
            );
            writer.newLine();

            for (WalkForwardMonthlyResult result : results) {
                writer.write(String.join(",",
                        String.valueOf(result.window()),
                        result.isStart().toString(),
                        result.isEnd().toString(),
                        result.oosStart().toString(),
                        result.oosEnd().toString(),
                        String.valueOf(result.tp()),
                        String.valueOf(result.tph()),
                        String.valueOf(result.sl()),
                        String.valueOf(result.initialEquity()),
                        String.valueOf(result.finalEquity()),
                        String.valueOf(result.returnPct()),
                        String.valueOf(result.trades()),
                        String.valueOf(result.winPercentage())
                ));
                writer.newLine();
            }
        }
    }
}
