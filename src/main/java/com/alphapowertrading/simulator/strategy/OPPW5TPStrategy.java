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
import java.time.Month;

//@Component("oppw")
public class OPPW5TPStrategy implements Strategy {

    @Override
    public void initialize(
            Broker broker,
            MarketData marketData
    ) {
        // Optional initialization.
    }

    @Override
    public void onCandle(
            MarketContext context,
            Broker broker
    ) {
        Candle candle =
                context.candle();
        DayOfWeek dayOfWeek = candle.date().getDayOfWeek();

        if (broker.hasOpenPosition()) {
            long positionEntry = broker.position().entryPrice();
            long entryTP = (long) (positionEntry * 1.03);
            long entrySL = (long) (positionEntry * (1-0.05));


            if (candle.open()<=entrySL){
                broker.sell(candle.date(), candle.open(), "OPEN<=SL");
            }
            if (candle.open() >= entryTP) {
                broker.sell(candle.date(), candle.open(), "OPEN>TP");
            }
            if (candle.low() <= entrySL) {
                broker.sell(candle.date(), entrySL, "LOW<=SL");
            }

            if (isLastDayOfWeek(context.marketData(), context.index())) {
                broker.sell(candle.date(), candle.close(),"FRIDAY CLOSE");
            }


        } else {
            if (dayOfWeek == DayOfWeek.MONDAY
                    && ! candle.date().isEqual(LocalDate.of(2020, Month.NOVEMBER,9))) { //open at Monday
                int sharesQuantity = (int) (broker.cash() / (candle.open() * 0.01));
                if (sharesQuantity > 0) {
                    broker.buy(candle.date(), candle.open(), sharesQuantity, BuyType.LUNES);
                }
            }
        }
    }

    @Override
    public void finish(
            Candle candle,
            MarketData marketData,
            Broker broker
    ) {
        // Optional final action.
    }

    public boolean isLastDayOfWeek(
            MarketData marketData,
            int index
    ) {
        if (index >= marketData.size() - 1) {
            return true;
        }

        LocalDate currentDate =
                marketData.get(index).date();

        LocalDate nextDate =
                marketData.get(index + 1).date();

        return !currentDate
                .isBefore(nextDate)
                || currentDate.getDayOfWeek() == DayOfWeek.FRIDAY
                || nextDate.getDayOfWeek() != currentDate.getDayOfWeek().plus(1);
    }
}
