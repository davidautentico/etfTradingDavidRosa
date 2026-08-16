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

    private final double tp;
    private final double tph;
    private final double sl;

    public OPPWFleuryStrategy() {
        this(0.040, 0.065, 0.020);
    }

    public OPPWFleuryStrategy(double tp, double tph, double sl) {
        this.tp = tp;
        this.tph = tph;
        this.sl = sl;
    }

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
        long tpval = (long) (entry * (1 + tp));
        long slval = (long) (entry * (1 - sl));
        DayOfWeek dayOfWeek = candle.date().getDayOfWeek();

        //Entry TP
        tpval = (long) (entry * (1 + tp));
        if (candle.open()>=tpval) {
            broker.sell(candle.date(), candle.open(), "OTP " + dayOfWeek);
            double closeDiff = (candle.open()-candle.close())/(double)candle.open();
            if (closeDiff<=0.01 //&& dayOfWeek==DayOfWeek.TUESDAY
            ){
                buy(context,candle.close(),broker,BuyType.NO_LUNES);
            }
            return;
        }

        //Low SL
        slval = (long) (entry * (1 - sl));
        if (candle.open()>slval && candle.low()<=slval){
            broker.sell(candle.date(), slval, "SLL " + dayOfWeek);
            return;
        }

        //High TP
        tpval = (long) (entry * (1 + tph));
        if (candle.open()<tpval && candle.high()>=tpval){
            broker.sell(candle.date(), tpval, "TPH " + dayOfWeek);
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