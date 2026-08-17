package com.alphapowertrading.simulator.analytics.weekly;

import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;

/**
 * Monday entry / weekly HIGH, LOW and CLOSE analysis.
 *
 * Entry:
 *   Monday OPEN
 *
 * HIGH:
 *   maximum HIGH from Monday through the last trading day.
 *
 * LOW:
 *   minimum LOW from Monday through the last trading day.
 *
 * CLOSE:
 *   close of the last trading day of the week.
 *
 * All three outcomes are expressed as a return percentage
 * relative to Monday's OPEN.
 *
 * The rolling HIGH/LOW averages include the current week
 * and the previous three weeks.
 */
public class MondayWeeklyRolling4Analyzer {

    public List<MondayWeeklyRollingResult> analyze(
            MarketData marketData
    ) {
        List<WeeklyData> weeks =
                buildWeeks(marketData);

        List<MondayWeeklyRollingResult> results =
                new ArrayList<>();

        for (int i = 0; i < weeks.size(); i++) {

            WeeklyData week =
                    weeks.get(i);

            List<WeeklyData> window =
                    weeks.subList(
                            Math.max(0, i - 3),
                            i + 1
                    );

            double averageHigh =
                    window.stream()
                            .mapToDouble(
                                    WeeklyData::highReturn
                            )
                            .average()
                            .orElse(0);

            double averageLow =
                    window.stream()
                            .mapToDouble(
                                    WeeklyData::lowReturn
                            )
                            .average()
                            .orElse(0);

            results.add(
                    new MondayWeeklyRollingResult(
                            week.date(),
                            week.mondayOpen(),
                            week.high(),
                            week.low(),
                            week.highReturn(),
                            week.lowReturn(),
                            week.closeReturn(),
                            week.highDate(),
                            week.lowDate(),
                            week.closeDate(),
                            averageHigh,
                            averageLow,
                            window.size()
                    )
            );
        }

        return results;
    }

    private List<WeeklyData> buildWeeks(
            MarketData marketData
    ) {
        List<WeeklyData> weeks =
                new ArrayList<>();

        if (marketData == null || marketData.size() == 0) {
            return weeks;
        }

        int index = 0;

        while (index < marketData.size()) {

            Candle first =
                    marketData.get(index);

            /*
             * The position is opened on Monday.
             * Therefore weeks without a Monday are ignored.
             */
            if (first.date().getDayOfWeek()
                    != DayOfWeek.MONDAY) {

                index++;
                continue;
            }

            int end = index;

            long high =
                    first.high();

            long low =
                    first.low();

            LocalDate highDate =
                    LocalDate.from(first.date());

            LocalDate lowDate =
                    LocalDate.from(first.date());

            while (end + 1 < marketData.size()
                    && sameWeek(
                    first.date().toLocalDate(),
                    marketData.get(end + 1).date().toLocalDate()
            )) {

                end++;

                Candle candle =
                        marketData.get(end);

                if (candle.high() > high) {
                    high =
                            candle.high();

                    highDate =
                            LocalDate.from(candle.date());
                }

                if (candle.low() < low) {
                    low =
                            candle.low();

                    lowDate =
                            LocalDate.from(candle.date());
                }
            }

            Candle lastTradingDay =
                    marketData.get(end);

            long mondayOpen =
                    first.open();

            double highReturn =
                    returnPct(
                            mondayOpen,
                            high
                    );

            double lowReturn =
                    returnPct(
                            mondayOpen,
                            low
                    );

            double closeReturn =
                    returnPct(
                            mondayOpen,
                            lastTradingDay.close()
                    );

            weeks.add(
                    new WeeklyData(
                            first.date().toLocalDate(),
                            mondayOpen,
                            high,
                            low,
                            highReturn,
                            lowReturn,
                            closeReturn,
                            highDate,
                            lowDate,
                            lastTradingDay.date().toLocalDate()
                    )
            );

            index =
                    end + 1;
        }

        return weeks;
    }

    private double returnPct(
            long entry,
            long exit
    ) {
        if (entry == 0) {
            return 0;
        }

        return (double) exit / entry - 1.0;
    }

    private boolean sameWeek(
            LocalDate first,
            LocalDate second
    ) {
        WeekFields fields =
                WeekFields.ISO;

        return first.get(
                        fields.weekBasedYear()
                )
                == second.get(
                        fields.weekBasedYear()
                )
                &&
                first.get(
                        fields.weekOfWeekBasedYear()
                )
                ==
                second.get(
                        fields.weekOfWeekBasedYear()
                );
    }

    private record WeeklyData(
            LocalDate date,
            long mondayOpen,
            long high,
            long low,
            double highReturn,
            double lowReturn,
            double closeReturn,
            LocalDate highDate,
            LocalDate lowDate,
            LocalDate closeDate
    ) {
    }
}
