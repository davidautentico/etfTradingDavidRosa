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

@Component("fleury")
public class OPPWFleuryStrategy implements Strategy {

    private static final double TP = 0.035;
    private static final double BE_ACTIVATION = 0.915;
    private static final double BE_TP = 0.025;
    private static final double SL = 9.93;

    @Override
    public void onCandle(MarketContext context, Broker broker) {
        Candle candle = context.candle();

        if (broker.hasOpenPosition()) {
            managePosition(context, broker);
            return;
        }

        if (isMonday(candle) && !candle.date().equals(LocalDate.of(2020, 11, 9))) {
            buy(context, broker);
        }else{
            if (!context.isFirstCandle()){
                Candle yesterdayCandle = context.marketData().get(context.index()-1);
                if (yesterdayCandle.open()>= yesterdayCandle.close()*1.04
                        && candle.date().getDayOfWeek()==DayOfWeek.TUESDAY){
                    //buy(context, broker);
                }
            }
        }
    }

    private void managePosition(MarketContext context, Broker broker) {
        Candle candle = context.candle();
        long entry = broker.position().entryPrice();
        long tp = (long) (entry * (1 + TP));
        long sl = (long) (entry * (1 - SL));
        double actualProfit = (candle.open() - entry) / (double) entry;
        double actualHProfit = (candle.high() - entry) / (double) entry;
        double actualLProfit = (candle.low() - entry) / (double) entry;
        DayOfWeek dayOfWeek = candle.date().getDayOfWeek();

        // limit profit for 7%
        //System.out.printf("actualProfit %.2f%%%n", actualProfit*100);
        if (actualProfit>=TP) {
            broker.sell(candle.date(), candle.open(), "OTP " + dayOfWeek);

            long reentry = (long) (candle.open() * (1 - SL));
            if (dayOfWeek==DayOfWeek.TUESDAY && candle.low()<= reentry){
                //System.out.println("cerrado y reentrada "+candle.open()+" "+reentry);
                buy(context, reentry, broker, BuyType.NO_LUNES);
            }
            return;
        }


        // Close any remaining position at the end of the trading week.
        if (isLastDayOfWeek(context.marketData(), context.index())) {
            broker.sell(candle.date(), candle.close(), "WEEKLY_CLOSE");
        }
    }

    private void buy(MarketContext context, long reentry, Broker broker, BuyType buyType) {

        Candle candle = context.candle();
        double allocation = allocationForDrawdown(context.drawdown());
        double price = reentry * 0.01;
        int shares = (int) (broker.cash() * allocation / price);

        if (shares > 0) {
            broker.buy(candle.date(), reentry, shares, buyType);
        }
    }

    private void buy(MarketContext context, Broker broker) {
        Candle candle = context.candle();
        double allocation = allocationForDrawdown(context.drawdown());
        double price = candle.open() * 0.01;
        int shares = (int) (broker.cash() * allocation / price);

        if (shares > 0) {
            broker.buy(candle.date(), candle.open(), shares, BuyType.LUNES);
        }
    }

    private double allocationForDrawdown(double drawdown) {
        return 1.00;
    }

    private boolean isMonday(Candle candle) {
        return candle.date().getDayOfWeek() == DayOfWeek.MONDAY;
    }

    private boolean isLastDayOfWeek(MarketData marketData, int index) {
        if (index >= marketData.size() - 1) return true;

        LocalDate current = marketData.get(index).date();
        LocalDate next = marketData.get(index + 1).date();

        return current.getDayOfWeek() == DayOfWeek.FRIDAY || !current.plusDays(1).equals(next);
    }
}