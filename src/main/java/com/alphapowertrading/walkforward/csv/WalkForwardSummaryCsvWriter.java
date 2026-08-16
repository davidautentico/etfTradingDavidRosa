package com.alphapowertrading.walkforward.csv;

import com.alphapowertrading.walkforward.model.WalkForwardSummary;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class WalkForwardSummaryCsvWriter {

    public void write(String outputFile, WalkForwardSummary summary) throws IOException {
        Path path = Path.of(outputFile);

        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("initialEquity,finalEquity,cagr,sharpe,volatility,maxDrawdown,calmar,months,trades");
            writer.newLine();
            writer.write(String.join(",",
                    String.valueOf(summary.initialEquity()),
                    String.valueOf(summary.finalEquity()),
                    String.valueOf(summary.cagr()),
                    String.valueOf(summary.sharpe()),
                    String.valueOf(summary.volatility()),
                    String.valueOf(summary.maxDrawdown()),
                    String.valueOf(summary.calmar()),
                    String.valueOf(summary.months()),
                    String.valueOf(summary.trades())
            ));
            writer.newLine();
        }
    }
}
