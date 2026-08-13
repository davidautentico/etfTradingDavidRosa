package com.alphapowertrading.simulator.core.chart;

import com.alphapowertrading.simulator.core.broker.Trade;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.report.BacktestReport;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.ohlc.OHLCSeries;
import org.jfree.data.time.ohlc.OHLCSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.annotations.XYTextAnnotation;

import org.springframework.stereotype.Component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class JFreeChartGenerator {

    private static final Color BULLISH = new Color(34, 139, 84);
    private static final Color BEARISH = new Color(205, 65, 65);
    private static final Color BUY_COLOR = new Color(0, 130, 70);
    private static final Color SELL_COLOR = new Color(190, 40, 40);
    private static final Color TRADE_LINE_COLOR = new Color(120, 120, 120, 150);

    public void generate(
            String symbol,
            double initialCapital,
            MarketData marketData,
            BacktestReport report,
            Path outputDirectory
    ) throws IOException {

        if (marketData == null || marketData.size() == 0) {
            throw new IllegalArgumentException("Market data is empty");
        }

        Files.createDirectories(outputDirectory);

        // Remove all charts previously generated for this symbol before
        // creating the new yearly charts. This avoids leaving obsolete
        // files when the strategy/data changes between simulations.
        deletePreviousSymbolCharts(symbol, outputDirectory);

        /*
         * Generamos una imagen independiente para cada año
         * existente en MarketData.
         */
        List<Integer> years = marketData.candles().stream()
                .map(candle -> candle.date().getYear())
                .distinct()
                .sorted()
                .toList();

        for (Integer year : years) {

            List<Candle> candles = marketData.candles().stream()
                    .filter(candle -> candle.date().getYear() == year)
                    .toList();

            if (candles.isEmpty()) {
                continue;
            }

            /*
             * Incluimos los trades que tengan entrada o salida
             * durante el año representado.
             */
            List<Trade> trades = report.trades().stream()
                    .filter(trade ->
                            trade.entryDate().getYear() == year
                                    || trade.exitDate().getYear() == year)
                    .toList();

            List<Double> equityCurve = report.equityCurve();

            if (equityCurve == null
                    || equityCurve.size() < marketData.size()) {
                throw new IllegalStateException(
                        "Equity curve must contain one value per candle"
                );
            }

            int firstIndex = findFirstIndexForYear(
                    marketData,
                    year
            );

            int lastIndex = findLastIndexForYear(
                    marketData,
                    year
            );

            double yearInitialCapital =
                    firstIndex == 0
                            ? initialCapital
                            : equityCurve.get(firstIndex - 1);

            double yearFinalCapital =
                    equityCurve.get(lastIndex);

            double yearGain =
                    yearInitialCapital == 0
                            ? 0.0
                            : yearFinalCapital / yearInitialCapital - 1.0;

            // MaxDD is calculated from equity, not from candle prices.
            double yearMaxDrawdown =
                    calculateEquityMaxDrawdown(
                            equityCurve,
                            firstIndex,
                            lastIndex
                    );

            JFreeChart chart = createChart(
                    symbol,
                    candles,
                    trades,
                    report,
                    year,
                    yearInitialCapital,
                    yearFinalCapital,
                    yearGain,
                    yearMaxDrawdown
            );

            String gainText =
                    formatMetricForFileName(yearGain * 100);

            String maxDdText =
                    formatMetricForFileName(yearMaxDrawdown * 100);

            Path outputFile =
                    outputDirectory.resolve(
                            String.format(
                                    "%s-backtest-%d-Gain_%s%%-Capital_%s_to_%s-MaxDD_%s%%.png",
                                    symbol,
                                    year,
                                    gainText,
                                    formatMetricForFileName(yearInitialCapital),
                                    formatMetricForFileName(yearFinalCapital),
                                    maxDdText
                            )
                    );

            ChartUtils.saveChartAsPNG(
                    outputFile.toFile(),
                    chart,
                    1800,
                    950
            );
        }
    }

    private void deletePreviousSymbolCharts(
            String symbol,
            Path outputDirectory
    ) throws IOException {

        String prefix = symbol + "-";

        try (var files = Files.list(outputDirectory)) {
            files
                    .filter(Files::isRegularFile)
                    .filter(path ->
                            path.getFileName()
                                    .toString()
                                    .startsWith(prefix)
                    )
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException(
                                    "Unable to delete previous chart: "
                                            + path.toAbsolutePath(),
                                    e
                            );
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    private JFreeChart createChart(
            String symbol,
            List<Candle> candles,
            List<Trade> trades,
            BacktestReport report,
            int year,
            double yearInitialCapital,
            double yearFinalCapital,
            double yearGain,
            double yearMaxDrawdown
    ) {
        OHLCSeries ohlcSeries =
                new OHLCSeries("Price");

        for (Candle candle : candles) {
            ohlcSeries.add(
                    new Day(
                            java.sql.Date.valueOf(candle.date())
                    ),
                    price(candle.open()),
                    price(candle.high()),
                    price(candle.low()),
                    price(candle.close())
            );
        }

        OHLCSeriesCollection priceDataset =
                new OHLCSeriesCollection();

        priceDataset.addSeries(ohlcSeries);

        DateAxis dateAxis = new DateAxis("Date");
        dateAxis.setDateFormatOverride(
                new SimpleDateFormat("dd-MMM")
        );

        NumberAxis priceAxis =
                new NumberAxis("Price");

        priceAxis.setAutoRangeIncludesZero(false);

        CandlestickRenderer candleRenderer =
                new CandlestickRenderer();

        candleRenderer.setDrawVolume(false);
        candleRenderer.setUseOutlinePaint(true);

        /*
         * JFreeChart utiliza el itemPaint para el cuerpo.
         * Asignamos colores por índice mediante un renderer
         * personalizado para distinguir bullish/bearish.
         */
        candleRenderer =
                new BullBearCandlestickRenderer(candles);

        XYPlot plot =
                new XYPlot(
                        priceDataset,
                        dateAxis,
                        priceAxis,
                        candleRenderer
                );

        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(new Color(225, 225, 225));
        plot.setRangeGridlinePaint(new Color(225, 225, 225));
        plot.setInsets(new RectangleInsets(10, 10, 10, 10));

        addTradeDataset(
                plot,
                candles,
                trades,
                year
        );

        String title =
                String.format(
                        "%s | %d | Ganancia: %.2f%% | Capital: %.2f -> %.2f | MaxDD: %.2f%% | Trades: %d",
                        symbol,
                        year,
                        yearGain * 100,
                        yearInitialCapital,
                        yearFinalCapital,
                        yearMaxDrawdown * 100,
                        trades.size()
                );

        JFreeChart chart =
                new JFreeChart(
                        title,
                        new Font(
                                "SansSerif",
                                Font.BOLD,
                                18
                        ),
                        plot,
                        true
                );

        chart.setBackgroundPaint(Color.WHITE);

        return chart;
    }

    private String formatMetricForFileName(double value) {
        return String.format(
                java.util.Locale.US,
                "%.2f",
                value
        ).replace("-", "m");
    }

    private double calculateEquityMaxDrawdown(
            List<Double> equityCurve,
            int firstIndex,
            int lastIndex
    ) {
        if (firstIndex < 0 || lastIndex < firstIndex) {
            return 0.0;
        }

        double peak = equityCurve.get(firstIndex);
        double maxDrawdown = 0.0;

        for (int i = firstIndex; i <= lastIndex; i++) {
            double equity = equityCurve.get(i);
            peak = Math.max(peak, equity);

            if (peak > 0.0) {
                maxDrawdown = Math.min(
                        maxDrawdown,
                        equity / peak - 1.0
                );
            }
        }

        return maxDrawdown;
    }

    private int findFirstIndexForYear(
            MarketData marketData,
            int year
    ) {
        for (int i = 0; i < marketData.size(); i++) {
            if (marketData.get(i).date().getYear() == year) {
                return i;
            }
        }

        return -1;
    }

    private int findLastIndexForYear(
            MarketData marketData,
            int year
    ) {
        for (int i = marketData.size() - 1; i >= 0; i--) {
            if (marketData.get(i).date().getYear() == year) {
                return i;
            }
        }

        return -1;
    }

    private void addTradeDataset(
            XYPlot plot,
            List<Candle> candles,
            List<Trade> trades,
            int year
    ) {
        XYSeries buys =
                new XYSeries("Buy");

        XYSeries sells =
                new XYSeries("Sell");

        for (Trade trade : trades) {

            if (trade.entryDate().getYear() == year) {
                buys.add(
                        toMillis(trade.entryDate()),
                        price(trade.entryPrice())
                );
            }

            if (trade.exitDate().getYear() == year) {
                sells.add(
                        toMillis(trade.exitDate()),
                        price(trade.exitPrice())
                );
            }

            /*
             * Línea entre entrada y salida.
             *
             * Se añade como anotación directamente al plot
             * para no crear una serie diferente por cada trade.
             */
            if (trade.entryDate().getYear() == year
                    && trade.exitDate().getYear() == year) {

                plot.addAnnotation(
                        new org.jfree.chart.annotations.XYLineAnnotation(
                                toMillis(trade.entryDate()),
                                price(trade.entryPrice()),
                                toMillis(trade.exitDate()),
                                price(trade.exitPrice()),
                                new BasicStroke(
                                        1.0f,
                                        BasicStroke.CAP_BUTT,
                                        BasicStroke.JOIN_BEVEL,
                                        0,
                                        new float[]{4f, 4f},
                                        0
                                ),
                                TRADE_LINE_COLOR
                        )
                );
            }

            /*
             * Etiqueta pequeña únicamente en la salida.
             * Contiene la razón del cierre y el PnL.
             */
            if (trade.exitDate().getYear() == year) {

                double pnlPercent =
                        trade.entryPrice() == 0
                                ? 0
                                : (
                                (double) trade.exitPrice()
                                        / trade.entryPrice()
                                        - 1
                        ) * 100;

                String reason =
                        trade.closeReason() == null
                                ? ""
                                : trade.closeReason();

                XYTextAnnotation annotation =
                        new XYTextAnnotation(
                                String.format(
                                        "%s %.2f%%",
                                        reason,
                                        pnlPercent
                                ),
                                toMillis(trade.exitDate()),
                                price(trade.exitPrice())
                        );

                annotation.setFont(
                        new Font(
                                "SansSerif",
                                Font.PLAIN,
                                8
                        )
                );

                annotation.setPaint(
                        pnlPercent >= 0
                                ? BUY_COLOR
                                : SELL_COLOR
                );

                plot.addAnnotation(annotation);
            }
        }

        XYSeriesCollection tradeDataset =
                new XYSeriesCollection();

        tradeDataset.addSeries(buys);
        tradeDataset.addSeries(sells);

        XYLineAndShapeRenderer tradeRenderer =
                new XYLineAndShapeRenderer(
                        false,
                        true
                );

        Shape marker =
                new Ellipse2D.Double(
                        -4,
                        -4,
                        8,
                        8
                );

        tradeRenderer.setSeriesShape(0, marker);
        tradeRenderer.setSeriesShape(1, marker);

        tradeRenderer.setSeriesPaint(
                0,
                BUY_COLOR
        );

        tradeRenderer.setSeriesPaint(
                1,
                SELL_COLOR
        );

        tradeRenderer.setSeriesOutlinePaint(
                0,
                Color.WHITE
        );

        tradeRenderer.setSeriesOutlinePaint(
                1,
                Color.WHITE
        );

        tradeRenderer.setUseOutlinePaint(true);

        plot.setDataset(1, tradeDataset);
        plot.setRenderer(1, tradeRenderer);
    }

    private double price(long value) {
        return value * 0.01;
    }

    private double toMillis(LocalDate date) {
        return date.atStartOfDay(
                ZoneId.systemDefault()
        ).toInstant().toEpochMilli();
    }
}
