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
import java.util.Locale;

@Component("fleuryday")
public class OPPWFleuryDailyStrategy implements Strategy {

    private final double ENTRY_BIAS = 0.01;

    private final double tp;
    private final double tph;
    private final double sl;
    private final double weakTp;

    public OPPWFleuryDailyStrategy () {
        this(0.99, 0.99, 0.99, 0.99);
    }

    public OPPWFleuryDailyStrategy (double tp, double tph, double sl, double weakTp) {
        this.tp = tp;
        this.tph = tph;
        this.sl = sl;
        this.weakTp = weakTp;
    }

    @Override
    public void onCandle(MarketContext context, Broker broker) {
        Candle candle = context.candle();

        if (broker.hasOpenPosition()) {
            managePosition(context, broker);
            return;
        }

        if (true
                //&& isMonday(candle)
                && !candle.date().equals(LocalDate.of(2020, 11, 9))
                && candle.high()>=candle.open()*(1+ENTRY_BIAS)
        ) {
            buy(context, (long) (candle.open()*(1+ENTRY_BIAS)),broker,BuyType.LUNES);
            managePosition(context, broker);
        }
    }

    private void managePosition(MarketContext context, Broker broker) {
        if (!broker.hasOpenPosition()) return;

        Candle candle = context.candle();
        long entry = broker.position().entryPrice();
        long tpval = (long) (entry * (1 + tp));
        long slval = (long) (entry * (1 - sl));
        DayOfWeek dayOfWeek = candle.date().getDayOfWeek();

        double actualProfit = (double) (candle.open() - entry) /entry;


        //Entry TP
        tpval = (long) (entry * (1 + tp));
        if (candle.open()>=tpval) {
            broker.sell(candle.date(), candle.open(), "OTP " + dayOfWeek);
            double closeDiff = (candle.open()-candle.close())/(double)candle.open();
            if (closeDiff<=0.01
            ){
                //buy(context,candle.close(),broker,BuyType.NO_LUNES);
            }
            return;
        }

        //Close on weakness
        if (actualProfit<=0){
            tpval = (long) (entry * (1 + weakTp));
            if (candle.high()>=tpval){
                broker.sell(candle.date(), tpval, "WEAK " + dayOfWeek);
                return;
            }
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
            printMaeMfaFromIndex(context, tpval);
            return;
        }

        if (context.index() >= broker.position().index()+0){
            broker.sell(candle.date(), candle.close(), "DAY_CLOSE");
        }
    }

    private void printMaeMfaFromIndex(MarketContext context, long reference) {
        Candle actualCandle = context.candle();
        int lastIndex = context.marketData().size()-1;
        long high = actualCandle.high();
        long low = actualCandle.low();

        for (int i=context.index();i<lastIndex;i++){
            actualCandle = context.marketData().get(i);
            DayOfWeek dayOfWeek = actualCandle.date().getDayOfWeek();
            if (actualCandle.high()>high){
                high = actualCandle.high();
            }
            if (actualCandle.low()<low){
                low = actualCandle.low();
            }

            if (dayOfWeek==DayOfWeek.FRIDAY){
                double mfa = (double) (high - reference)*100.0 /reference;
                double mae = (double) (reference - low)*100.0 /reference;
                System.out.printf(Locale.GERMAN, "%8.3f;%8.3f%n",mfa,mae);
                break;
            }
        }
    }

    private void buy(MarketContext context, long reentry, Broker broker, BuyType buyType) {

        Candle candle = context.candle();
        double allocation = allocationForDrawdown(context.drawdown());
        double price = reentry * 0.01;
        int shares = (int) (broker.cash() * allocation / price);

        if (shares > 0) {
            broker.buy(candle.date(),context.index(), reentry, shares, buyType);
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

    private boolean isWednesday(Candle candle) {
        return candle.date().getDayOfWeek() == DayOfWeek.WEDNESDAY;
    }

    private boolean isThursday(Candle candle) {
        return candle.date().getDayOfWeek() == DayOfWeek.THURSDAY;
    }

    private boolean isFriday(Candle candle) {
        return candle.date().getDayOfWeek() == DayOfWeek.FRIDAY;
    }

    private boolean isLastDayOfWeek(MarketData marketData, int index) {
        if (index >= marketData.size() - 1) return true;

        LocalDate current = LocalDate.from(marketData.get(index).date());
        LocalDate next = LocalDate.from(marketData.get(index + 1).date());

        return current.getDayOfWeek() == DayOfWeek.FRIDAY || !current.plusDays(1).equals(next);
    }
}