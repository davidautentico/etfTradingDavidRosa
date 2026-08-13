package com.alphapowertrading.simulator.analytics.weekly;

import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;

/**
 * Independent statistic to evaluate a possible Tuesday re-entry.
 *
 * It compares weeks according to the Monday result:
 *
 *   NEGATIVE MONDAY:
 *       Monday CLOSE < Monday OPEN
 *
 *   POSITIVE MONDAY:
 *       Monday CLOSE > Monday OPEN
 *
 * For each week the potential Tuesday re-entry is measured from
 * Tuesday OPEN:
 *
 *   Tuesday -> week CLOSE
 *   Tuesday -> remaining-week HIGH
 *   Tuesday -> remaining-week LOW
 *
 * The HIGH/LOW windows start on Tuesday, because the hypothetical
 * re-entry happens at Tuesday OPEN.
 */
public class TuesdayReentryAnalyzer {

    public List<TuesdayReentryResult> analyze(
            MarketData marketData
    ) {
        List<TuesdayReentryResult> results =
                new ArrayList<>();

        if (marketData == null || marketData.size() == 0) {
            return results;
        }

        int index = 0;

        while (index < marketData.size()) {

            Candle monday =
                    marketData.get(index);

            if (monday.date().getDayOfWeek()
                    != DayOfWeek.MONDAY) {
                index++;
                continue;
            }

            int end =
                    findWeekEnd(
                            marketData,
                            index
                    );

            int tuesdayIndex =
                    index + 1;

            /*
             * A Tuesday must exist immediately after Monday in the
             * chronological trading data. If Tuesday is a holiday,
             * there is no Tuesday re-entry and the week is skipped.
             */
            if (tuesdayIndex > end
                    || marketData.get(tuesdayIndex)
                    .date()
                    .getDayOfWeek()
                    != DayOfWeek.TUESDAY) {

                index = end + 1;
                continue;
            }

            Candle tuesday =
                    marketData.get(tuesdayIndex);

            long weeklyHigh =
                    tuesday.high();

            long weeklyLow =
                    tuesday.low();

            LocalDate highDate =
                    tuesday.date();

            LocalDate lowDate =
                    tuesday.date();

            /*
             * The remaining-week HIGH/LOW starts on Tuesday.
             */
            for (int i = tuesdayIndex + 1; i <= end; i++) {

                Candle candle =
                        marketData.get(i);

                if (candle.high() > weeklyHigh) {
                    weeklyHigh = candle.high();
                    highDate = candle.date();
                }

                if (candle.low() < weeklyLow) {
                    weeklyLow = candle.low();
                    lowDate = candle.date();
                }
            }

            Candle weekClose =
                    marketData.get(end);

            double mondayReturn =
                    returnPct(
                            monday.open(),
                            monday.close()
                    );

            boolean mondayNegative =
                    monday.close() < monday.open();

            results.add(
                    new TuesdayReentryResult(
                            monday.date(),
                            mondayReturn,
                            mondayNegative,
                            tuesday.date(),
                            tuesday.open(),
                            returnPct(
                                    tuesday.open(),
                                    weekClose.close()
                            ),
                            returnPct(
                                    tuesday.open(),
                                    weeklyHigh
                            ),
                            returnPct(
                                    tuesday.open(),
                                    weeklyLow
                            ),
                            weekClose.date(),
                            weekClose.close(),
                            highDate,
                            lowDate
                    )
            );

            index = end + 1;
        }

        return results;
    }

    private int findWeekEnd(
            MarketData marketData,
            int start
    ) {
        Candle monday =
                marketData.get(start);

        int end = start;

        while (end + 1 < marketData.size()
                && sameWeek(
                        monday.date(),
                        marketData.get(end + 1).date()
                )) {
            end++;
        }

        return end;
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
}
