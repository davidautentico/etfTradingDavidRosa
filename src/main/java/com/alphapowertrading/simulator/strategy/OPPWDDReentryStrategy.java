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
public class OPPWDDReentryStrategy implements Strategy {

    private static final double TP = 0.02;
    private static final double SL = 0.05;

    @Override
    public void onCandle(MarketContext context, Broker broker) {

        Candle candle = context.candle();

        if (broker.hasOpenPosition()) {
            managePosition(context, broker);
            return;
        }

        if (isMonday(candle)
                && !candle.date().equals(LocalDate.of(2020, 11, 9))) {
            buy(context, broker);
            managePosition(context,broker);
        }
    }

    private void managePosition(
            MarketContext context,
            Broker broker
    ) {
        Candle candle = context.candle();
        long entry = broker.position().entryPrice();

        long tp = (long) (entry * (1 + TP));
        long sl = (long) (entry * (1 - SL));

        if (candle.open() <= sl) {
            broker.sell(candle.date(), candle.open(), "OPEN<=SL");
            return;
        }

        if (candle.open() >= tp) {
            broker.sell(candle.date(), candle.open(), "OPEN>=TP");
            return;
        }

        if (candle.high() >= tp) {
            broker.sell(candle.date(), tp, "HIGH>=TP");
            return;
        }

        if (candle.low() <= sl) {
            broker.sell(candle.date(), sl, "LOW<=SL");
            return;
        }

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
        Candle candle = context.candle();

        double allocation =
                allocationForDrawdown(context.drawdown());

        double price = candle.open() * 0.01;
        int shares = (int) (
                broker.cash() * allocation / price
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

    private double allocationForDrawdown(
            double drawdown
    ) {
        if (drawdown <= -50) return 1.00;
        if (drawdown <= -40)  return 0.90;
        if (drawdown <= -30)  return 0.80;
        if (drawdown <= -20)  return 0.70;
        if (drawdown <= -10)  return 0.60;
        return 0.50;
    }

    private boolean isMonday(Candle candle) {
        return candle.date().getDayOfWeek() == DayOfWeek.MONDAY;
    }

    private boolean isLastDayOfWeek(
            MarketData marketData,
            int index
    ) {
        if (index >= marketData.size() - 1) {
            return true;
        }

        LocalDate current =
                marketData.get(index).date();

        LocalDate next =
                marketData.get(index + 1).date();

        return current.getDayOfWeek() == DayOfWeek.FRIDAY
                || !current.plusDays(1).equals(next);
    }
}