package com.alphapowertrading.simulator.core.chart;

import com.alphapowertrading.simulator.core.broker.Trade;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.report.BacktestReport;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@Component
public class BacktestChartGenerator {

    private static final int YEAR = 2026;
    private static final int HEIGHT = 700;
    private static final int TOP = 30;
    private static final int BOTTOM = 55;
    private static final int LEFT = 70;
    private static final int RIGHT = 90;
    private static final int CANDLE_SLOT = 12;
    private static final int MIN_BODY_WIDTH = 5;
    private static final int MAX_BODY_WIDTH = 9;

    public void generate(
            MarketData marketData,
            BacktestReport report,
            Path outputFile
    ) throws IOException {

        List<Candle> candles = marketData.candles().stream()
                .filter(candle -> candle.date().getYear() == YEAR)
                .toList();

        List<Trade> trades = report.trades().stream()
                .filter(trade ->
                        trade.entryDate().getYear() == YEAR
                                || trade.exitDate().getYear() == YEAR)
                .toList();

        if (candles.isEmpty()) {
            throw new IllegalStateException(
                    "No candles found for year " + YEAR
            );
        }

        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String svg = buildSvg(candles, trades);

        String html = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Backtest - %d</title>
                    <style>
                        * {
                            box-sizing: border-box;
                        }

                        body {
                            margin: 0;
                            padding: 14px;
                            background: #f4f6f8;
                            color: #222;
                            font-family: Arial, sans-serif;
                        }

                        .panel {
                            background: white;
                            border: 1px solid #d5d9de;
                            border-radius: 8px;
                            padding: 12px;
                        }

                        .stats {
                            display: flex;
                            gap: 20px;
                            flex-wrap: wrap;
                            margin-bottom: 12px;
                            font-size: 13px;
                        }

                        .chart-wrapper {
                            width: 100%%;
                            height: 700px;
                            overflow-x: auto;
                            overflow-y: hidden;
                            background: white;
                            border: 1px solid #ccd2d8;
                        }

                        svg {
                            display: block;
                            background: white;
                        }

                        .grid {
                            stroke: #e5e8eb;
                            stroke-width: 1;
                        }

                        .axis-text {
                            fill: #68707a;
                            font-size: 11px;
                        }

                        .wick {
                            stroke-width: 1.2;
                        }

                        .bullish {
                            fill: #20a464;
                            stroke: #20a464;
                        }

                        .bearish {
                            fill: #dc4c4c;
                            stroke: #dc4c4c;
                        }

                        .entry {
                            fill: #087f3d;
                            stroke: white;
                            stroke-width: 1;
                        }

                        .exit {
                            fill: #b51f1f;
                            stroke: white;
                            stroke-width: 1;
                        }

                        .trade-line {
                            stroke: #888;
                            stroke-width: 1;
                            stroke-dasharray: 4 4;
                        }

                        .hint {
                            margin-top: 8px;
                            color: #666;
                            font-size: 12px;
                        }
                    </style>
                </head>
                <body>
                    <div class="panel">
                        <div class="stats">
                            <strong>Backtest — %d</strong>
                            <span>Candles: %d</span>
                            <span>Final: %.2f</span>
                            <span>CAGR: %.2f%%</span>
                            <span>MaxDD: %.2f%%</span>
                            <span>Sharpe: %.2f</span>
                            <span>Trades: %d</span>
                        </div>

                        <div class="chart-wrapper">
                            %s
                        </div>

                        <div class="hint">
                            🟢 Entrada &nbsp;&nbsp;
                            🔴 Salida &nbsp;&nbsp;
                            Verde = alcista &nbsp;&nbsp;
                            Rojo = bajista &nbsp;&nbsp;
                            Desplaza horizontalmente para recorrer 2026.
                            Pasa el ratón por una vela o trade para ver sus datos.
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                YEAR,
                YEAR,
                candles.size(),
                report.finalEquity(),
                report.cagr() * 100,
                report.maxDrawdown() * 100,
                report.sharpeRatio(),
                report.trades().size(),
                svg
        );

        Files.writeString(outputFile, html);
    }

    private String buildSvg(
            List<Candle> candles,
            List<Trade> trades
    ) {
        int chartWidth =
                LEFT + RIGHT + candles.size() * CANDLE_SLOT;

        int chartHeight = HEIGHT;

        long minPrice = candles.stream()
                .mapToLong(Candle::low)
                .min()
                .orElseThrow();

        long maxPrice = candles.stream()
                .mapToLong(Candle::high)
                .max()
                .orElseThrow();

        double padding =
                Math.max((maxPrice - minPrice) * 0.05, 1);

        double min = minPrice - padding;
        double max = maxPrice + padding;

        StringBuilder svg = new StringBuilder();

        svg.append("""
                <svg xmlns="http://www.w3.org/2000/svg"
                     width="%d"
                     height="%d"
                     viewBox="0 0 %d %d">
                """.formatted(
                chartWidth,
                chartHeight,
                chartWidth,
                chartHeight
        ));

        svg.append("""
                <rect x="0" y="0" width="100%" height="100%" fill="white"/>
                """);

        drawGrid(svg, chartWidth, chartHeight, min, max);

        drawCandles(
                svg,
                candles,
                chartHeight,
                min,
                max
        );

        drawTrades(
                svg,
                candles,
                trades,
                chartHeight,
                min,
                max
        );

        drawDates(
                svg,
                candles,
                chartHeight
        );

        svg.append("</svg>");

        return svg.toString();
    }

    private void drawGrid(
            StringBuilder svg,
            int width,
            int height,
            double min,
            double max
    ) {
        int chartHeight =
                height - TOP - BOTTOM;

        int rows = 8;

        for (int i = 0; i <= rows; i++) {
            double y =
                    TOP + (double) i * chartHeight / rows;

            double price =
                    max - i * (max - min) / rows;

            svg.append("""
                    <line class="grid"
                          x1="%f"
                          y1="%f"
                          x2="%f"
                          y2="%f"/>
                    <text class="axis-text"
                          x="%f"
                          y="%f">%s</text>
                    """.formatted(
                    (double) LEFT,
                    y,
                    (double) width - RIGHT,
                    y,
                    (double) width - RIGHT + 8,
                    y + 4,
                    formatPrice(price)
            ));
        }
    }

    private void drawCandles(
            StringBuilder svg,
            List<Candle> candles,
            int height,
            double min,
            double max
    ) {
        int chartHeight =
                height - TOP - BOTTOM;

        double slot =
                (double) (candles.size() * CANDLE_SLOT)
                        / candles.size();

        double bodyWidth =
                Math.max(
                        MIN_BODY_WIDTH,
                        Math.min(MAX_BODY_WIDTH, slot * 0.65)
                );

        for (int i = 0; i < candles.size(); i++) {
            Candle candle = candles.get(i);

            double x =
                    LEFT + i * CANDLE_SLOT + CANDLE_SLOT / 2.0;

            double highY =
                    priceToY(candle.high(), min, max, chartHeight);

            double lowY =
                    priceToY(candle.low(), min, max, chartHeight);

            double openY =
                    priceToY(candle.open(), min, max, chartHeight);

            double closeY =
                    priceToY(candle.close(), min, max, chartHeight);

            boolean bullish =
                    candle.close() >= candle.open();

            String css =
                    bullish ? "bullish" : "bearish";

            /*
             * Mecha HIGH -> LOW.
             */
            svg.append("""
                    <line class="wick %s"
                          x1="%f"
                          y1="%f"
                          x2="%f"
                          y2="%f"/>
                    """.formatted(
                    css,
                    x,
                    highY,
                    x,
                    lowY
            ));

            /*
             * Cuerpo rectangular OPEN -> CLOSE.
             */
            double top =
                    Math.min(openY, closeY);

            double bodyHeight =
                    Math.max(1, Math.abs(closeY - openY));

            String tooltip = """
                    %s
                    Open: %s
                    High: %s
                    Low: %s
                    Close: %s
                    """.formatted(
                    candle.date(),
                    formatPrice(candle.open()),
                    formatPrice(candle.high()),
                    formatPrice(candle.low()),
                    formatPrice(candle.close())
            );

            svg.append("""
                    <rect class="%s"
                          x="%f"
                          y="%f"
                          width="%f"
                          height="%f">
                        <title>%s</title>
                    </rect>
                    """.formatted(
                    css,
                    x - bodyWidth / 2,
                    top,
                    bodyWidth,
                    bodyHeight,
                    escapeXml(tooltip)
            ));
        }
    }

    private void drawTrades(
            StringBuilder svg,
            List<Candle> candles,
            List<Trade> trades,
            int height,
            double min,
            double max
    ) {
        int chartHeight =
                height - TOP - BOTTOM;

        for (Trade trade : trades) {

            int entryIndex =
                    findCandleIndex(candles, trade.entryDate().toLocalDate());

            int exitIndex =
                    findCandleIndex(candles, trade.exitDate().toLocalDate());

            /*
             * Trade que empieza en 2025 y termina en 2026:
             * no tiene marcador de entrada dentro del gráfico,
             * pero sí se muestra la salida.
             */

            if (entryIndex >= 0 && exitIndex >= 0) {
                double x1 =
                        candleX(entryIndex);

                double y1 =
                        priceToY(
                                trade.entryPrice(),
                                min,
                                max,
                                chartHeight
                        );

                double x2 =
                        candleX(exitIndex);

                double y2 =
                        priceToY(
                                trade.exitPrice(),
                                min,
                                max,
                                chartHeight
                        );

                svg.append("""
                        <line class="trade-line"
                              x1="%f"
                              y1="%f"
                              x2="%f"
                              y2="%f"/>
                        """.formatted(
                        x1, y1, x2, y2
                ));
            }

            if (entryIndex >= 0) {
                drawEntry(
                        svg,
                        candles,
                        trade,
                        entryIndex,
                        chartHeight,
                        min,
                        max
                );
            }

            if (exitIndex >= 0) {
                drawExit(
                        svg,
                        candles,
                        trade,
                        exitIndex,
                        chartHeight,
                        min,
                        max
                );
            }
        }
    }

    private void drawEntry(
            StringBuilder svg,
            List<Candle> candles,
            Trade trade,
            int index,
            int chartHeight,
            double min,
            double max
    ) {
        double x = candleX(index);

        double y =
                priceToY(
                        trade.entryPrice(),
                        min,
                        max,
                        chartHeight
                );

        String tooltip = """
                BUY
                Date: %s
                Price: %s
                Type: %s
                Quantity: %d
                """.formatted(
                trade.entryDate(),
                formatPrice(trade.entryPrice()),
                trade.buyType() == null
                        ? ""
                        : trade.buyType().name(),
                trade.quantity()
        );

        svg.append("""
                <polygon class="entry"
                         points="%f,%f %f,%f %f,%f">
                    <title>%s</title>
                </polygon>
                """.formatted(
                x,
                y - 11,
                x - 7,
                y + 3,
                x + 7,
                y + 3,
                escapeXml(tooltip)
        ));
    }

    private void drawExit(
            StringBuilder svg,
            List<Candle> candles,
            Trade trade,
            int index,
            int chartHeight,
            double min,
            double max
    ) {
        double x = candleX(index);

        double y =
                priceToY(
                        trade.exitPrice(),
                        min,
                        max,
                        chartHeight
                );

        double pnlPercent =
                trade.entryPrice() == 0
                        ? 0
                        : ((double) trade.exitPrice()
                        / trade.entryPrice() - 1) * 100;

        String tooltip = """
                SELL
                Date: %s
                Price: %s
                Reason: %s
                PnL: %.2f%%
                """.formatted(
                trade.exitDate(),
                formatPrice(trade.exitPrice()),
                trade.closeReason(),
                pnlPercent
        );

        svg.append("""
                <polygon class="exit"
                         points="%f,%f %f,%f %f,%f">
                    <title>%s</title>
                </polygon>
                """.formatted(
                x,
                y + 11,
                x - 7,
                y - 3,
                x + 7,
                y - 3,
                escapeXml(tooltip)
        ));
    }

    private void drawDates(
            StringBuilder svg,
            List<Candle> candles,
            int height
    ) {
        int step =
                Math.max(1, candles.size() / 12);

        for (int i = 0; i < candles.size(); i += step) {
            Candle candle = candles.get(i);

            svg.append("""
                    <text class="axis-text"
                          x="%f"
                          y="%d"
                          text-anchor="middle">%s</text>
                    """.formatted(
                    candleX(i),
                    height - 15,
                    candle.date()
            ));
        }
    }

    private int findCandleIndex(
            List<Candle> candles,
            java.time.LocalDate date
    ) {
        for (int i = 0; i < candles.size(); i++) {
            if (candles.get(i).date().equals(date)) {
                return i;
            }
        }

        return -1;
    }

    private double candleX(int index) {
        return LEFT
                + index * CANDLE_SLOT
                + CANDLE_SLOT / 2.0;
    }

    private double priceToY(
            long price,
            double min,
            double max,
            int chartHeight
    ) {
        return TOP
                + (max - price)
                * chartHeight
                / (max - min);
    }

    private String formatPrice(double value) {
        return String.format(
                Locale.US,
                "%.2f",
                value / 100.0
        );
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
