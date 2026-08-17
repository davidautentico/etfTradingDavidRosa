package com.alphapowertrading.simulator.strategy;

import com.alphapowertrading.simulator.core.broker.Broker;
import com.alphapowertrading.simulator.core.broker.BuyType;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketContext;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.strategy.Strategy;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component("smaMeanReverting")
public class SMAMeanReverting implements Strategy {

    private static final long MINIPIPS_PER_PIP = 10;

    private final int smaLength;
    private final double distancePips;
    private final double tpPips;
    private final double slPips;

    public SMAMeanReverting() {
        this(10, 15, 15, 45);
    }

    public SMAMeanReverting(
            int smaLength,
            double distancePips,
            double tpPips,
            double slPips
    ) {
        if (smaLength <= 0) {
            throw new IllegalArgumentException(
                    "SMA length must be greater than zero"
            );
        }

        if (distancePips < 0) {
            throw new IllegalArgumentException(
                    "Distance cannot be negative"
            );
        }

        if (tpPips <= 0) {
            throw new IllegalArgumentException(
                    "TP must be greater than zero"
            );
        }

        if (slPips <= 0) {
            throw new IllegalArgumentException(
                    "SL must be greater than zero"
            );
        }

        this.smaLength = smaLength;
        this.distancePips = distancePips;
        this.tpPips = tpPips;
        this.slPips = slPips;
    }

    @Override
    public void onCandle(
            MarketContext context,
            Broker broker
    ) {
        Candle candle = context.candle();

        if (broker.hasOpenPosition()) {
            managePosition(context, broker);
            return;
        }

        if (!hasEnoughHistory(context)) {
            return;
        }

        double sma = calculateSma(context);

        long distance =
                pipsToMinipips(distancePips);

        long shortEntryLevel =
                (long) Math.ceil(sma + distance);

        /*
         * Mean reversion SHORT:
         *
         * Entramos solamente cuando el OPEN está
         * suficientemente por encima de la SMA.
         */
        if (candle.open() >= shortEntryLevel) {
            openShort(context, broker);
        }
    }

    private void managePosition(
            MarketContext context,
            Broker broker
    ) {
        if (broker.position().side()
                != com.alphapowertrading.simulator.core.broker.PositionSide.SHORT) {
            return;
        }

        Candle candle = context.candle();

        long entry =
                broker.position().entryPrice();

        long tp =
                entry - pipsToMinipips(tpPips);

        long sl =
                entry + pipsToMinipips(slPips);

        /*
         * SL se comprueba antes que TP.
         *
         * Si la misma candle toca ambos niveles,
         * se considera que primero se ejecutó el SL.
         */

        // SL por apertura
        if (candle.open() >= sl) {
            broker.buyToCover(
                    candle.date(),
                    candle.open(),
                    "SL_OPEN_SHORT"
            );
            return;
        }

        // SL intrabar
        if (candle.high() >= sl) {
            broker.buyToCover(
                    candle.date(),
                    sl,
                    "SL_SHORT"
            );
            return;
        }

        // TP por apertura
        if (candle.open() <= tp) {
            broker.buyToCover(
                    candle.date(),
                    candle.open(),
                    "TP_OPEN_SHORT"
            );
            return;
        }

        // TP intrabar
        if (candle.low() <= tp) {
            broker.buyToCover(
                    candle.date(),
                    tp,
                    "TP_SHORT"
            );
            return;
        }

        /*
         * Cerramos cualquier posición que quede abierta
         * al final de la semana.
         */
        if (isLastDayOfWeek(
                context.marketData(),
                context.index()
        )) {
            broker.buyToCover(
                    candle.date(),
                    candle.close(),
                    "WEEKLY_CLOSE_SHORT"
            );
        }
    }

    private void openShort(
            MarketContext context,
            Broker broker
    ) {
        Candle candle = context.candle();

        double price =
                candle.open() * 0.01;

        int quantity =
                (int) (
                        broker.cash() / price
                );

        if (quantity > 0) {
            broker.shortSell(
                    candle.date(),
                    candle.open(),
                    quantity,
                    BuyType.NO_LUNES
            );
        }
    }

    private double calculateSma(
            MarketContext context
    ) {
        MarketData marketData =
                context.marketData();

        int index =
                context.index();

        /*
         * La SMA utiliza únicamente los cierres
         * anteriores a la candle actual.
         *
         * Esto evita look-ahead bias.
         */
        long sum = 0;

        for (
                int i = index - smaLength;
                i < index;
                i++
        ) {
            sum += marketData.get(i).close();
        }

        return (double) sum / smaLength;
    }

    private boolean hasEnoughHistory(
            MarketContext context
    ) {
        return context.index() >= smaLength;
    }

    private long pipsToMinipips(
            double pips
    ) {
        return Math.round(
                pips * MINIPIPS_PER_PIP
        );
    }

    private boolean isLastDayOfWeek(
            MarketData marketData,
            int index
    ) {
        if (index >= marketData.size() - 1) {
            return true;
        }

        LocalDate current =
                marketData.get(index).date().toLocalDate();

        LocalDate next =
                marketData.get(index + 1).date().toLocalDate();

        return current.getDayOfWeek().getValue() >= 5
                || !current.plusDays(1).equals(next);
    }
}