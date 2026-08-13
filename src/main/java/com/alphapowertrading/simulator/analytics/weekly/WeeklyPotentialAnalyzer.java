package com.alphapowertrading.simulator.analytics.weekly;

import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;

/**
 * For every trading day of every week, calculates three theoretical exits.
 *
 * Entry = OPEN of the selected entry day.
 *
 * 1. HIGH:
 *    Maximum HIGH from the entry candle through the last
 *    trading day of the same week.
 *
 * 2. LOW:
 *    Minimum LOW from the entry candle through the last
 *    trading day of the same week.
 *
 * 3. CLOSE:
 *    CLOSE of the last trading day of the same week.
 *
 * IMPORTANT:
 * The HIGH/LOW window starts on the entry day.
 *
 * Example:
 *
 * Wednesday entry:
 *
 * Wednesday HIGH
 * Thursday HIGH
 * Friday HIGH
 *
 * and:
 *
 * Wednesday LOW
 * Thursday LOW
 * Friday LOW
 *
 * are used to calculate the theoretical gain/loss.
 *
 * These HIGH/LOW calculations are ex-post theoretical values and
 * do not imply that the exact intraday extreme was executable.
 */
public class WeeklyPotentialAnalyzer {

    public List<WeeklyPotentialResult> analyze(
            MarketData marketData
    ) {
        List<WeeklyPotentialResult> results =
                new ArrayList<>();

        if (marketData == null || marketData.size() == 0) {
            return results;
        }

        int weekStartIndex = 0;

        while (weekStartIndex < marketData.size()) {

            Candle first =
                    marketData.get(weekStartIndex);

            int weekEndIndex =
                    findWeekEndIndex(
                            marketData,
                            weekStartIndex
                    );

            Candle lastTradingDay =
                    marketData.get(weekEndIndex);

            /*
             * Every trading day can be an entry day.
             */
            for (
                    int entryIndex = weekStartIndex;
                    entryIndex <= weekEndIndex;
                    entryIndex++
            ) {

                Candle entry =
                        marketData.get(entryIndex);

                long weeklyHigh =
                        entry.high();

                long weeklyLow =
                        entry.low();

                LocalDate highDate =
                        entry.date();

                LocalDate lowDate =
                        entry.date();

                /*
                 * IMPORTANT:
                 *
                 * Start the HIGH/LOW calculation at the
                 * entry candle, NOT at Monday.
                 *
                 * Therefore a Wednesday entry only sees:
                 *
                 * Wednesday -> Thursday -> Friday
                 */
                for (
                        int i = entryIndex + 1;
                        i <= weekEndIndex;
                        i++
                ) {

                    Candle candle =
                            marketData.get(i);

                    if (candle.high() > weeklyHigh) {
                        weeklyHigh =
                                candle.high();

                        highDate =
                                candle.date();
                    }

                    if (candle.low() < weeklyLow) {
                        weeklyLow =
                                candle.low();

                        lowDate =
                                candle.date();
                    }
                }

                double maxGain =
                        returnPct(
                                entry.open(),
                                weeklyHigh
                        );

                double maxLoss =
                        returnPct(
                                entry.open(),
                                weeklyLow
                        );

                double closeReturn =
                        returnPct(
                                entry.open(),
                                lastTradingDay.close()
                        );

                results.add(
                        new WeeklyPotentialResult(
                                first.date(),
                                entry.date().getDayOfWeek(),
                                entry.date(),
                                entry.open(),

                                highDate,
                                weeklyHigh,
                                maxGain,

                                lowDate,
                                weeklyLow,
                                maxLoss,

                                lastTradingDay.date(),
                                lastTradingDay.close(),
                                closeReturn
                        )
                );
            }

            weekStartIndex =
                    weekEndIndex + 1;
        }

        return results;
    }

    private int findWeekEndIndex(
            MarketData marketData,
            int startIndex
    ) {
        Candle first =
                marketData.get(startIndex);

        int index = startIndex;

        while (index + 1 < marketData.size()
                && sameWeek(
                first.date(),
                marketData.get(index + 1).date()
        )) {
            index++;
        }

        return index;
    }

    private double returnPct(
            long entryPrice,
            long exitPrice
    ) {
        if (entryPrice == 0) {
            return 0;
        }

        return (double) exitPrice
                / entryPrice
                - 1.0;
    }

    private boolean sameWeek(
            LocalDate first,
            LocalDate second
    ) {
        WeekFields weekFields =
                WeekFields.ISO;

        return first.get(
                        weekFields.weekBasedYear()
                )
                == second.get(
                        weekFields.weekBasedYear()
                )
                &&
                first.get(
                        weekFields.weekOfWeekBasedYear()
                )
                ==
                second.get(
                        weekFields.weekOfWeekBasedYear()
                );
    }
}
