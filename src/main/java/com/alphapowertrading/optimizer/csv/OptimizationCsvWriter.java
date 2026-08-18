package com.alphapowertrading.optimizer.csv;

import com.alphapowertrading.optimizer.model.OptimizationResult;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class OptimizationCsvWriter {

    public void write(
            String fileName,
            List<OptimizationResult> results,
            String sortBy) {

        List<OptimizationResult> sorted =
                results.stream()
                        .sorted(comparator(sortBy))
                        .toList();

        Path path = Path.of(fileName);

        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            try (BufferedWriter writer =
                         Files.newBufferedWriter(path)) {

                writer.write(
                        "TP,TPH,SL,SHARPE,CAGR,MAX_DD,CALMAR,FINAL_EQUITY,TRADES,WIN_RATE"
                );
                writer.newLine();

                for (OptimizationResult result : sorted) {
                    writer.write(String.format(
                            Locale.US,
                            "%.5f,%.5f,%.5f,%.6f,%.6f,%.6f,%.6f,%.2f,%d,%.4f",
                            result.tp(),
                            result.tph(),
                            result.sl(),
                            result.sharpe(),
                            result.cagr(),
                            result.maxDrawdown(),
                            result.calmar(),
                            result.finalEquity(),
                            result.trades(),
                            result.winRate()
                    ));
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to write optimization CSV: " + path,
                    e
            );
        }
    }

    private Comparator<OptimizationResult> comparator(String sortBy) {
        if (sortBy == null) {
            sortBy = "sharpe";
        }

        return switch (sortBy.toLowerCase(Locale.ROOT)) {
            case "cagr" ->
                    Comparator.comparingDouble(
                            OptimizationResult::cagr
                    ).reversed();

            case "maxdd" ->
                    Comparator.comparingDouble(
                            OptimizationResult::maxDrawdown
                    ).reversed();

            case "calmar" ->
                    Comparator.comparingDouble(
                            OptimizationResult::calmar
                    ).reversed();

            case "finalequity" ->
                    Comparator.comparingDouble(
                            OptimizationResult::finalEquity
                    ).reversed();

            case "winrate" ->
                    Comparator.comparingDouble(
                            OptimizationResult::winRate
                    ).reversed();

            default ->
                    Comparator.comparingDouble(
                            OptimizationResult::sharpe
                    ).reversed();
        };
    }
}
