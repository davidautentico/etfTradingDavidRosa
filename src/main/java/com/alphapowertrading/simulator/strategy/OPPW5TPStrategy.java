package com.alphapowertrading.simulator.strategy;

import com.alphapowertrading.simulator.core.broker.Broker;
import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;
import com.alphapowertrading.simulator.core.strategy.Strategy;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;

@Component("oppw")
public class OPPW5TPStrategy implements Strategy {

    @Override
    public void initialize(Broker broker, MarketData marketData) {
        Strategy.super.initialize(broker, marketData);
    }

    @Override
    public void onCandle(Candle candle, MarketData marketData, int index, Broker broker) {

        DayOfWeek dayOfWeek = candle.date().getDayOfWeek();
        if (broker.hasOpenPosition()) {
            if (dayOfWeek == DayOfWeek.FRIDAY) { //normal close at Friday closing
                broker.sell(candle.date(), candle.close());
            } else if (dayOfWeek != DayOfWeek.MONDAY){
                Candle yesterdayCandle = marketData.get(index - 1);
                long yesterdayClose = yesterdayCandle.close();

                if (dayOfWeek == DayOfWeek.TUESDAY && yesterdayClose < broker.position().entryPrice()) { //if yesterday close below entry price close BA if possible
                    if (candle.high() >= broker.position().entryPrice()) {
                        System.out.println("actual and yesterday: "+candle+" "+yesterdayCandle);
                      //broker.sell(candle.date(), broker.position().entryPrice());
                    }
                } else {

                    long entryTP = (long) (broker.position().entryPrice() * 1.25);

                    /*System.out.println("entryPrice, high y entryTP: " + broker.position().entryPrice()
                            + " " + candle.high() + " " + entryTP);*/
                    if (candle.high() >= entryTP) {
                        System.out.println("closed at TP");
                        broker.sell(candle.date(), entryTP);
                    }
                }
            }
        } else if (dayOfWeek == DayOfWeek.MONDAY) { //open at Monday
            int sharesQuantity = (int) (broker.cash() / (candle.open() * 0.01));
            if (sharesQuantity > 0) {
                broker.buy(candle.date(), candle.open(), sharesQuantity);
            }
        }
        {

        }
    }

    @Override
    public void finish(Candle lastCandle, MarketData marketData, Broker broker) {
        Strategy.super.finish(lastCandle, marketData, broker);
    }
}
