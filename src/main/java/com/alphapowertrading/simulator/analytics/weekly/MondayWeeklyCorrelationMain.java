package com.alphapowertrading.simulator.analytics.weekly;

import com.alphapowertrading.simulator.core.loader.CsvLoader;
import com.alphapowertrading.simulator.core.loader.CsvParser;
import com.alphapowertrading.simulator.core.market.MarketData;
import java.nio.file.Path;

public class MondayWeeklyCorrelationMain {

  public static void main(String[] args) throws Exception {

    AnalyticsConfig config =
            AnalyticsConfig.load(args);

    Path csvFile =
            Path.of(
                    config.dataDirectory(),
                    config.symbol() + ".csv"
            );

    System.out.println();
    System.out.println(
            "=============================================================="
    );
    System.out.println(
            "       WEEKLY HIGH / MONDAY OPEN CORRELATION"
    );
    System.out.println(
            "=============================================================="
    );
    System.out.println();

    System.out.println(
            "Symbol: " + config.symbol()
    );

    System.out.println(
            "CSV: " + csvFile.toAbsolutePath()
    );

    CsvLoader csvLoader =
            new CsvParser();

    MarketData marketData =
            csvLoader.load(csvFile);

    System.out.println(
            "Candles loaded: " + marketData.size()
    );

    System.out.println();

    MondayWeeklyCorrelationAnalyzer analyzer =
            new MondayWeeklyCorrelationAnalyzer();

    analyzer.analyze(marketData);
  }
}