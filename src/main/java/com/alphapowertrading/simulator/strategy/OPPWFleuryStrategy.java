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

    private static final double TP = 0.050;
    private static final double TPH = 0.07;
    private static final double SL = 0.0035;

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

        //Entry TP
        tp = (long) (entry * (1 + TP));
        if (candle.open()>=tp) {
            broker.sell(candle.date(), candle.open(), "OTP " + dayOfWeek);
            return;
        }

        //Low SL
        sl = (long) (entry * (1 - SL));
        if (candle.open()>sl && candle.low()<=sl){
            broker.sell(candle.date(), sl, "SLL " + dayOfWeek);
            return;
        }

        //High TP
        tp = (long) (entry * (1 + TPH));
        if (candle.open()<tp && candle.high()>=tp){
            broker.sell(candle.date(), tp, "TPH " + dayOfWeek);
            return;
        }

        //High TP
        /*long tpo = (long) (entry * (1 + 0.05));
        if (candle.open()<sl && candle.high()>=tpo){
            broker.sell(candle.date(), tpo, "TPO " + dayOfWeek);
            return;
        }*/

        /*if (!context.isFirstCandle()){
            Candle yCandle = context.marketData().get(context.index()-1);
            double yesterdayProfit = (yCandle.close() - yCandle.open()) / (double) yCandle.open();
            if (yesterdayProfit>-0.02 && yesterdayProfit<-0.00){
                broker.sell(candle.date(), candle.open(), "BETP " + dayOfWeek);
                return;
            }
        }*/


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

    private boolean isTuesday(Candle candle) {
        return candle.date().getDayOfWeek() == DayOfWeek.TUESDAY;
    }

    private boolean isLastDayOfWeek(MarketData marketData, int index) {
        if (index >= marketData.size() - 1) return true;

        LocalDate current = marketData.get(index).date();
        LocalDate next = marketData.get(index + 1).date();

        return current.getDayOfWeek() == DayOfWeek.FRIDAY || !current.plusDays(1).equals(next);
    }
}