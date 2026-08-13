package com.alphapowertrading.simulator.analytics.weekly;

import com.alphapowertrading.simulator.core.market.Candle;
import com.alphapowertrading.simulator.core.market.MarketData;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;

/**
 * Independent statistic:
 *
 * Selects only weeks where Monday CLOSE < Monday OPEN.
 *
 * Then studies what happens from Monday close until the
 * close of the last trading day of that week.
 *
 * It does not modify or depend on the other weekly analyzers.
 */
public class MondayNegativeWeekAnalyzer {

    public List<MondayNegativeWeekResult> analyze(
            MarketData marketData
    ) {
        List<MondayNegativeWeekResult> results =
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

            int end = index;

            long weeklyHigh = monday.high();
            long weeklyLow = monday.low();

            LocalDate highDate = monday.date();
            LocalDate lowDate = monday.date();

            while (end + 1 < marketData.size()
                    && sameWeek(
                            monday.date(),
                            marketData.get(end + 1).date()
                    )) {

                end++;

                Candle candle =
                        marketData.get(end);

                if (candle.high() > weeklyHigh) {
                    weeklyHigh = candle.high();
                    highDate = candle.date();
                }

                if (candle.low() < weeklyLow) {
                    weeklyLow = candle.low();
                    lowDate = candle.date();
                }
            }

            /*
             * Only keep weeks where Monday closes below Monday open.
             */
            if (monday.close() < monday.open()) {

                Candle last =
                        marketData.get(end);

                double mondayReturn =
                        returnPct(
                                monday.open(),
                                monday.close()
                        );

                double weekReturn =
                        returnPct(
                                monday.open(),
                                last.close()
                        );

                double recoveryFromMondayClose =
                        returnPct(
                                monday.close(),
                                last.close()
                        );

                double highReturn =
                        returnPct(
                                monday.open(),
                                weeklyHigh
                        );

                double lowReturn =
                        returnPct(
                                monday.open(),
                                weeklyLow
                        );

                boolean recovers =
                        last.close() > monday.close();

                results.add(
                        new MondayNegativeWeekResult(
                                monday.date(),
                                monday.open(),
                                monday.close(),
                                mondayReturn,
                                last.date(),
                                last.close(),
                                weekReturn,
                                recoveryFromMondayClose,
                                recovers,
                                highReturn,
                                lowReturn,
                                highDate,
                                lowDate
                        )
                );
            }

            index = end + 1;
        }

        return results;
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
        WeekFields fields = WeekFields.ISO;

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
