package com.alphapowertrading.statistical;

import com.alphapowertrading.statistical.model.Ohlc;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class StatisticalAnalysisRunner
    implements CommandLineRunner {

  private final StatisticalAnalysisProperties properties;
  private final OhlcCsvLoader loader;
  private final StatisticalAnalyzer analyzer;
  private final StatisticalAnalysisCsvWriter writer;

  public StatisticalAnalysisRunner(
      StatisticalAnalysisProperties properties,
      OhlcCsvLoader loader,
      StatisticalAnalyzer analyzer,
      StatisticalAnalysisCsvWriter writer) {
    this.properties = properties;
    this.loader = loader;
    this.analyzer = analyzer;
    this.writer = writer;
  }

  @Override
  public void run(String... args) throws Exception {
    if (!properties.enabled()) {
      return;
    }

    Path inputFile =
        Path.of(properties.inputFile());
    Path outputFile =
        Path.of(properties.outputFile());

    System.out.println(
        "==================================================");
    System.out.println("STATISTICAL ANALYSIS");
    System.out.println(
        "==================================================");
    System.out.println(
        "Input: " + inputFile.toAbsolutePath());
    System.out.println(
        "Output: " + outputFile.toAbsolutePath());
    System.out.println(
        "Direction: " + properties.direction());
    System.out.println(
        "SMA periods: " + properties.smaPeriods());
    System.out.println(
        "Close candles: " + properties.closeCandles());

    long start = System.currentTimeMillis();

    List<Ohlc> candles =
        loader.load(inputFile);

    System.out.printf(
        "Loaded %,d OHLC candles%n",
        candles.size());

    List<StatisticalResult> results =
        analyzer.analyze(
            candles,
            properties);

    writer.write(
        outputFile,
        results);

    double seconds =
        (System.currentTimeMillis() - start)
            / 1000.0;

    System.out.printf(
        "Operations: %,d%n",
        results.size());
    System.out.printf(
        "Completed in %.1f seconds%n",
        seconds);
    System.out.println(
        "==================================================");
  }
}
