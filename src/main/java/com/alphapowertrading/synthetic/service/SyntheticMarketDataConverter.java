package com.alphapowertrading.synthetic.service;

import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SyntheticMarketDataConverter {

    /**
     * Candle prices are stored in hundredths.
     * 1000.00 = 100000.
     */
    private static final long INITIAL_VALUE = 100000;

    public MarketData convert(
            MarketData baseData,
            double leverage) {

        validateInput(baseData, leverage);

        /*
         * The conversion MUST be performed chronologically:
         * oldest -> newest.
         */
        List<Candle> baseCandles =
                new ArrayList<>(baseData.candles());

        baseCandles.sort(
                (first, second) ->
                        first.date().compareTo(second.date()));

        List<Candle> syntheticCandles =
                new ArrayList<>(baseCandles.size() + 1);

        Candle firstBaseCandle =
                baseCandles.get(0);

        /*
         * Initial candle:
         *
         * One day before the first base date.
         *
         * Open  = 1000.00
         * High  = 1000.00
         * Low   = 1000.00
         * Close = 1000.00
         */
        syntheticCandles.add(
                new Candle(
                        firstBaseCandle.date().minusDays(1),
                        INITIAL_VALUE,
                        INITIAL_VALUE,
                        INITIAL_VALUE,
                        INITIAL_VALUE));

        long previousSyntheticLast =
                INITIAL_VALUE;

        for (int index = 0;
             index < baseCandles.size();
             index++) {

            Candle current =
                    baseCandles.get(index);

            /*
             * -------------------------------------------------
             * LAST / CLOSE
             * -------------------------------------------------
             *
             * The inter-session factor is calculated using
             * BASE LAST[n] / BASE LAST[n-1].
             *
             * SyntheticLast[n] =
             * SyntheticLast[n-1] *
             *
             * (1 + leverage *
             *   (BaseLast[n] - BaseLast[n-1])
             *   / BaseLast[n-1])
             */
            double lastReturn;

            if (index == 0) {

                /*
                 * For the first base session there is no
                 * previous base Last.
                 *
                 * The synthetic starts at 1000.00 and the
                 * first day's intraday movement is applied
                 * relative to the first base Last.
                 */
                lastReturn =
                        0.0;

            } else {

                Candle previous =
                        baseCandles.get(index - 1);

                lastReturn =
                        calculateReturn(
                                previous.close(),
                                current.close(),
                                leverage);
            }

            long syntheticLast =
                    calculatePrice(
                            previousSyntheticLast,
                            lastReturn);

            /*
             * -------------------------------------------------
             * OPEN
             * -------------------------------------------------
             *
             * Open is calculated relative to the BASE LAST
             * of the SAME session.
             *
             * SyntheticOpen[n] =
             * SyntheticLast[n] *
             *
             * (1 + leverage *
             *   (BaseOpen[n] - BaseLast[n])
             *   / BaseLast[n])
             */
            double openReturn =
                    calculateReturn(
                            current.close(),
                            current.open(),
                            leverage);

            long syntheticOpen =
                    calculatePrice(
                            syntheticLast,
                            openReturn);

            /*
             * -------------------------------------------------
             * HIGH
             * -------------------------------------------------
             */
            double highReturn =
                    calculateReturn(
                            current.close(),
                            current.high(),
                            leverage);

            long syntheticHigh =
                    calculatePrice(
                            syntheticLast,
                            highReturn);

            /*
             * -------------------------------------------------
             * LOW
             * -------------------------------------------------
             */
            double lowReturn =
                    calculateReturn(
                            current.close(),
                            current.low(),
                            leverage);

            long syntheticLow =
                    calculatePrice(
                            syntheticLast,
                            lowReturn);

            Candle syntheticCandle =
                    new Candle(
                            current.date(),
                            syntheticOpen,
                            syntheticHigh,
                            syntheticLow,
                            syntheticLast);

            syntheticCandles.add(
                    syntheticCandle);

            /*
             * The synthetic Last becomes the reference
             * for the next session.
             */
            previousSyntheticLast =
                    syntheticLast;
        }

        return new MarketData(
                syntheticCandles);
    }

    /**
     * Calculates:
     *
     * leverage * (current - reference) / reference
     */
    private double calculateReturn(
            long reference,
            long current,
            double leverage) {

        if (reference == 0) {
            throw new IllegalArgumentException(
                    "Base Last cannot be zero");
        }

        return leverage
                * ((double) current - reference)
                / reference;
    }

    private long calculatePrice(
            long basePrice,
            double returnValue) {

        return Math.round(
                basePrice
                        * (1.0 + returnValue));
    }

    private void validateInput(
            MarketData baseData,
            double leverage) {

        if (baseData == null
                || baseData.size() == 0) {

            throw new IllegalArgumentException(
                    "Base market data cannot be empty");
        }

        if (leverage < 1.1
                || leverage > 5.0) {

            throw new IllegalArgumentException(
                    "Leverage must be between "
                            + "1.1 and 5.0");
        }

        double rounded =
                Math.round(leverage * 10.0)
                        / 10.0;

        if (Math.abs(
                leverage - rounded)
                > 0.000001) {

            throw new IllegalArgumentException(
                    "Leverage must use "
                            + "increments of 0.1");
        }
    }
}