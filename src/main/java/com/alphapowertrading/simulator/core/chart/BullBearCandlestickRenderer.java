package com.alphapowertrading.simulator.core.chart;

import com.alphapowertrading.simulator.core.market.Candle;
import java.awt.Color;
import java.util.List;
import org.jfree.chart.renderer.xy.CandlestickRenderer;

/**
 * Candlestick renderer that colors each candle according to:
 *
 * close >= open -> bullish
 * close < open  -> bearish
 */
public class BullBearCandlestickRenderer
        extends CandlestickRenderer {

    private final List<Candle> candles;

    public BullBearCandlestickRenderer(
            List<Candle> candles
    ) {
        super();
        this.candles = candles;
        setDrawVolume(false);
        setUseOutlinePaint(true);
    }

    @Override
    public java.awt.Paint getItemPaint(
            int row,
            int column
    ) {
        Candle candle = candles.get(column);

        if (candle.close() >= candle.open()) {
            return new Color(34, 139, 84);
        }

        return new Color(205, 65, 65);
    }

    @Override
    public java.awt.Paint getItemOutlinePaint(
            int row,
            int column
    ) {
        return getItemPaint(row, column);
    }
}
