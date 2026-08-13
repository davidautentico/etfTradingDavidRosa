package com.alphapowertrading.simulator.strategy;

import com.alphapowertrading.simulator.core.broker.Broker;
import com.alphapowertrading.simulator.core.broker.BuyType;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketContext;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.strategy.Strategy;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;

//@Component("oppw")
public class OPPWModifiedStrategy implements Strategy {

    private static final double TP = 0.0125;

    // Gap activation threshold: -2.00%
    private static final double BE_ACTIVATION = 0.020;

    // Recovery target after gap activation: -1.75%
    private static final double BE_TP = 0.0175;

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

        if (isMonday(candle)
                && !candle.date().equals(
                LocalDate.of(2020, 11, 9)
        )) {

            buy(context, broker);
        }
    }

    private void managePosition(
            MarketContext context,
            Broker broker
    ) {

        Candle candle = context.candle();

        long entry =
                broker.position().entryPrice();

        long tp =
                (long) (
                        entry * (1 + TP)
                );

        DayOfWeek dayOfWeek =
                candle.date().getDayOfWeek();

        /*
         * Gap recovery mode.
         *
         * If the candle opens at least 2% below the entry price,
         * switch the target from the normal TP to the recovery target.
         */
        long beActivation =
                (long) (
                        entry * (1 - BE_ACTIVATION)
                );

        if (candle.open() < beActivation) {

            long tpModified =
                    (long) (
                            entry * (1 - BE_TP)
                    );

            /*
             * Exit if the price recovers to the recovery target
             * during the current candle.
             */
            if (candle.high() >= tpModified) {

                broker.sell(
                        candle.date(),
                        tpModified,
                        "BE " + dayOfWeek
                );

                return;
            }

        } else {

            /*
             * Positive gap.
             *
             * If the candle opens above the normal TP,
             * exit at the opening price and capture the full gap.
             */
            if (candle.open() >= tp) {

                broker.sell(
                        candle.date(),
                        candle.open(),
                        "OTP " + dayOfWeek
                );

                return;
            }

            /*
             * Normal take-profit.
             */
            if (candle.high() >= tp) {

                broker.sell(
                        candle.date(),
                        tp,
                        "HTP " + dayOfWeek
                );

                return;
            }
        }

        /*
         * Close any remaining position at the end of the trading week.
         */
        if (isLastDayOfWeek(
                context.marketData(),
                context.index()
        )) {

            broker.sell(
                    candle.date(),
                    candle.close(),
                    "WEEKLY_CLOSE"
            );
        }
    }

    private void buy(
            MarketContext context,
            Broker broker
    ) {

        Candle candle =
                context.candle();

        double allocation =
                allocationForDrawdown(
                        context.drawdown()
                );

        double price =
                candle.open() * 0.01;

        int shares =
                (int) (
                        broker.cash()
                                * allocation
                                / price
                );

        if (shares > 0) {

            broker.buy(
                    candle.date(),
                    candle.open(),
                    shares,
                    BuyType.LUNES
            );
        }
    }

    private void buy(
            MarketContext context,
            long priceToBuy,
            Broker broker
    ) {

        Candle candle =
                context.candle();

        double allocation =
                allocationForDrawdown(
                        context.drawdown()
                );

        double price =
                priceToBuy * 0.01;

        int shares =
                (int) (
                        broker.cash()
                                * allocation
                                / price
                );

        if (shares > 0) {

            broker.buy(
                    candle.date(),
                    priceToBuy,
                    shares,
                    BuyType.LUNES
            );
        }
    }

    private double allocationForDrawdown(
            double drawdown
    ) {
        return 1.00;
    }

    private boolean isMonday(
            Candle candle
    ) {
        return candle.date().getDayOfWeek()
                == DayOfWeek.MONDAY;
    }

    private boolean isLastDayOfWeek(
            MarketData marketData,
            int index
    ) {

        if (index >= marketData.size() - 1) {
            return true;
        }

        LocalDate current =
                marketData
                        .get(index)
                        .date();

        LocalDate next =
                marketData
                        .get(index + 1)
                        .date();

        return current.getDayOfWeek()
                == DayOfWeek.FRIDAY
                || !current
                .plusDays(1)
                .equals(next);
    }
}