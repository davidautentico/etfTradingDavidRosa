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
public class OPPWMonTueStrategy implements Strategy {

    private static final double TP = 0.010;
    private static final double BE_ACTIVATION = 0.035;
    private static final double BE_TP = 0.015;
    private static final double SL = 0.50;

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
        }else if (!context.isFirstCandle()) {
            Candle yesterdayCandle = context.marketData().get(context.index()-1);
            long closeDiff = yesterdayCandle.close()- yesterdayCandle.open();
            if (closeDiff<=-candle.open()*0.02){
                //buy(context, broker);
            }
        }{
            //buy(context, broker);
            //managePosition(context,broker);
        }
    }

    private void managePosition(
            MarketContext context,
            Broker broker
    ) {
        Candle candle = context.candle();
        long entry = broker.position().entryPrice();

        long tp = (long) (entry * (1 + TP));
        DayOfWeek dayOfWeek = candle.date().getDayOfWeek();

        if (dayOfWeek==DayOfWeek.MONDAY){
            if (candle.close()>entry){
                broker.sell(candle.date(), candle.close(), "MC " + dayOfWeek);
                return;
            }
        }

        /*if (dayOfWeek==DayOfWeek.TUESDAY){
            if (!context.isFirstCandle()){
                Candle yCandle = context.marketData().get(context.index()-1);
                if (yCandle.close()<entry){
                    buy(context, broker);
                }
            }
            if (candle.close()>entry){
                broker.sell(candle.date(), candle.close(), "MC " + dayOfWeek);
                return;
            }
        }*/

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

    private void buy(
            MarketContext context,
            long priceToBuy,
            Broker broker
    ) {
        Candle candle = context.candle();

        double allocation =
                allocationForDrawdown(context.drawdown());

        double price = priceToBuy * 0.01;
        int shares = (int) (
                broker.cash() * allocation / price
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