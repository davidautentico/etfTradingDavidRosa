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
import java.time.temporal.TemporalAdjusters;

@Component("fleuryV3")
public class OPPWSmaStrategy implements Strategy {

    /**
     * TP calculado el lunes y mantenido fijo durante
     * toda la operación.
     */
    private double targetPrice = Double.NaN;

    @Override
    public void onCandle(
            MarketContext context,
            Broker broker) {

        Candle candle = context.candle();

        /*
         * Si existe una posición abierta,
         * gestionamos la posición.
         */
        if (broker.hasOpenPosition()) {
            managePosition(context, broker);
            return;
        }

        /*
         * La estrategia solamente busca entradas
         * los lunes.
         */
        if (isMonday(candle)) {
            openPosition(context, broker);
        }
    }

    /**
     * Busca una entrada el lunes.
     *
     * Fórmulas:
     *
     * RANGE =
     *      High(W-1) - FridayClose(W-2)
     *
     * GAP =
     *      MondayOpen - FridayClose(W-1)
     *
     * ENTRADA:
     *      GAP < RANGE
     *
     * TP:
     *      FridayClose(W-1) + RANGE
     */
    private void openPosition(
            MarketContext context,
            Broker broker) {

        Candle mondayCandle =
                context.candle();

        WeeklyReference reference =
                calculateWeeklyReference(context);

        if (reference == null) {

            System.out.println(
                    "FLEURY V3 | No hay suficientes datos "
                            + "para calcular las semanas anteriores.");

            return;
        }

        /*
         * Mostrar todas las candles y cálculos
         * utilizados.
         */
        debugWeeklyCalculation(
                context,
                reference);

        /*
         * -------------------------------------------------
         * RANGE
         * -------------------------------------------------
         *
         * High de W-1 menos Friday Close de W-2.
         */
        double rangePoints =
                reference.previousWeekHigh()
                        - reference.previousFridayClose();

        /*
         * -------------------------------------------------
         * GAP DEL LUNES
         * -------------------------------------------------
         *
         * Monday Open menos Friday Close de W-1.
         */
        double mondayGap =
                (double) mondayCandle.open()
                        - reference.lastFridayClose();

        /*
         * -------------------------------------------------
         * CONDICIÓN DE ENTRADA
         * -------------------------------------------------
         */
        boolean entry =
                mondayGap < rangePoints;

        System.out.println();
        System.out.println(
                ">>> ENTRY CHECK");

        System.out.println(
                "    Monday Open       = "
                        + mondayCandle.open());

        System.out.println(
                "    Friday Close W-1  = "
                        + reference.lastFridayClose());

        System.out.println(
                "    Monday Gap        = "
                        + mondayGap);

        System.out.println(
                "    Range             = "
                        + rangePoints);

        System.out.println(
                "    Condition         = "
                        + mondayGap
                        + " < "
                        + rangePoints
                        + " = "
                        + entry);

        if (!entry) {

            System.out.println(
                    ">>> RESULT: NO BUY");

            return;
        }

        /*
         * -------------------------------------------------
         * TP
         * -------------------------------------------------
         *
         * MUY IMPORTANTE:
         *
         * El TP NO parte del Monday Open.
         *
         * Parte del Friday Close de W-1.
         *
         * TP =
         *      FridayClose(W-1) + Range
         *
         * Range =
         *      High(W-1) - FridayClose(W-2)
         */
        targetPrice =
                reference.lastFridayClose()
                        + rangePoints*0.6;

        System.out.println();
        System.out.println(
                ">>> RESULT: BUY");

        System.out.println(
                "    Entry             = "
                        + mondayCandle.open());

        System.out.println(
                "    Range             = "
                        + rangePoints);

        System.out.println(
                "    Target Price      = "
                        + targetPrice);

        System.out.println(
                "    Target Formula    = "
                        + reference.lastFridayClose()
                        + " + "
                        + rangePoints
                        + " = "
                        + targetPrice);

        buy(
                context,
                broker);
    }

    /**
     * Gestiona una posición abierta.
     *
     * TP:
     *      FridayClose(W-1) + Range
     *
     * Si se alcanza durante la semana:
     *      TP
     *
     * Si no:
     *      WEEKLY_CLOSE
     */
    private void managePosition(
            MarketContext context,
            Broker broker) {

        Candle candle =
                context.candle();

        if (Double.isNaN(targetPrice)) {

            System.out.println(
                    "FLEURY V3 | WARNING: posición abierta "
                            + "pero targetPrice es NaN");

            return;
        }

        /*
         * -------------------------------------------------
         * TP EN APERTURA
         * -------------------------------------------------
         *
         * Si el lunes o cualquier día abre por encima
         * del objetivo, ejecutamos al Open.
         */
        if (candle.open() >= targetPrice) {

            System.out.println(
                    ">>> SELL TP_OPEN"
                            + " | Date=" + candle.date()
                            + " | Open=" + candle.open()
                            + " | Target=" + targetPrice);

            broker.sell(
                    candle.date(),
                    candle.open(),
                    "TP_OPEN");

            targetPrice =
                    Double.NaN;

            return;
        }

        /*
         * -------------------------------------------------
         * TP INTRADAY
         * -------------------------------------------------
         *
         * Si el High alcanza el objetivo.
         */
        if (candle.high() >= targetPrice) {

            System.out.println(
                    ">>> SELL TP"
                            + " | Date=" + candle.date()
                            + " | High=" + candle.high()
                            + " | Target=" + targetPrice);

            broker.sell(
                    candle.date(),
                    (long) targetPrice,
                    "TP");

            targetPrice =
                    Double.NaN;

            return;
        }

        /*
         * -------------------------------------------------
         * CIERRE SEMANAL
         * -------------------------------------------------
         */
        if (isLastDayOfWeek(
                context.marketData(),
                context.index())) {

            System.out.println(
                    ">>> SELL WEEKLY_CLOSE"
                            + " | Date=" + candle.date()
                            + " | Close=" + candle.close()
                            + " | Target=" + targetPrice);

            broker.sell(
                    candle.date(),
                    candle.close(),
                    "WEEKLY_CLOSE");

            targetPrice =
                    Double.NaN;
        }
    }

    /**
     * Obtiene los datos necesarios para calcular
     * la operación del lunes.
     *
     * W-1:
     *      High máximo de toda la semana
     *      Friday Close
     *
     * W-2:
     *      Friday Close
     */
    private WeeklyReference calculateWeeklyReference(
            MarketContext context) {

        MarketData marketData =
                context.marketData();

        int currentIndex =
                context.index();

        LocalDate currentDate =
                LocalDate.from(
                        context.candle().date());

        /*
         * Lunes de la semana actual.
         */
        LocalDate currentMonday =
                currentDate.with(
                        TemporalAdjusters
                                .previousOrSame(
                                        DayOfWeek.MONDAY));

        /*
         * Lunes de W-1.
         */
        LocalDate previousMonday =
                currentMonday.minusWeeks(1);

        /*
         * Lunes de W-2.
         */
        LocalDate previousPreviousMonday =
                currentMonday.minusWeeks(2);

        /*
         * Viernes de W-1.
         */
        LocalDate previousFriday =
                currentMonday.minusDays(3);

        /*
         * Viernes de W-2.
         */
        LocalDate previousPreviousFriday =
                currentMonday.minusDays(10);

        double previousWeekHigh =
                Double.NEGATIVE_INFINITY;

        long lastFridayClose =
                0L;

        long previousFridayClose =
                0L;

        /*
         * Recorremos todas las candles anteriores
         * al lunes actual.
         */
        for (int i = 0;
             i < currentIndex;
             i++) {

            Candle candle =
                    marketData.get(i);

            LocalDate date =
                    LocalDate.from(
                            candle.date());

            /*
             * =============================================
             * W-1
             * =============================================
             */
            if (!date.isBefore(previousMonday)
                    && date.isBefore(currentMonday)) {

                /*
                 * High máximo de toda W-1.
                 */
                previousWeekHigh =
                        Math.max(
                                previousWeekHigh,
                                candle.high());

                /*
                 * Friday Close de W-1.
                 */
                if (date.equals(previousFriday)) {

                    lastFridayClose =
                            candle.close();
                }
            }

            /*
             * =============================================
             * W-2
             * =============================================
             */
            if (!date.isBefore(previousPreviousMonday)
                    && date.isBefore(previousMonday)) {

                /*
                 * Friday Close de W-2.
                 */
                if (date.equals(
                        previousPreviousFriday)) {

                    previousFridayClose =
                            candle.close();
                }
            }
        }

        /*
         * Comprobamos que tenemos todos los datos.
         */
        if (previousWeekHigh
                == Double.NEGATIVE_INFINITY
                || lastFridayClose <= 0
                || previousFridayClose <= 0) {

            return null;
        }

        return new WeeklyReference(
                previousWeekHigh,
                lastFridayClose,
                previousFridayClose);
    }

    /**
     * DEBUG
     *
     * Imprime todas las candles utilizadas para:
     *
     * W-1:
     *      High
     *      Friday Close
     *
     * W-2:
     *      Friday Close
     *
     * y muestra todas las fórmulas.
     */
    private void debugWeeklyCalculation(
            MarketContext context,
            WeeklyReference reference) {

        MarketData marketData =
                context.marketData();

        int currentIndex =
                context.index();

        LocalDate currentDate =
                LocalDate.from(
                        context.candle().date());

        LocalDate currentMonday =
                currentDate.with(
                        TemporalAdjusters
                                .previousOrSame(
                                        DayOfWeek.MONDAY));

        LocalDate previousMonday =
                currentMonday.minusWeeks(1);

        LocalDate previousPreviousMonday =
                currentMonday.minusWeeks(2);

        System.out.println();
        System.out.println(
                "============================================================");

        System.out.println(
                "                 FLEURY V3 WEEKLY DEBUG");

        System.out.println(
                "============================================================");

        System.out.println(
                "Current Monday       : "
                        + currentMonday);

        System.out.println(
                "Monday Open          : "
                        + context.candle().open());

        System.out.println(
                "W-1 Monday           : "
                        + previousMonday);

        System.out.println(
                "W-2 Monday           : "
                        + previousPreviousMonday);

        /*
         * =============================================
         * W-1
         * =============================================
         */
        System.out.println();
        System.out.println(
                "------------------------------------------------------------");

        System.out.println(
                "W-1 CANDLES");

        System.out.println(
                "------------------------------------------------------------");

        double calculatedWeeklyHigh =
                Double.NEGATIVE_INFINITY;

        long calculatedFridayClose =
                0L;

        for (int i = 0;
             i < currentIndex;
             i++) {

            Candle candle =
                    marketData.get(i);

            LocalDate date =
                    LocalDate.from(
                            candle.date());

            if (!date.isBefore(previousMonday)
                    && date.isBefore(currentMonday)) {

                System.out.printf(
                        "[%d] %s | O=%d H=%d L=%d C=%d%n",
                        i,
                        candle.date(),
                        candle.open(),
                        candle.high(),
                        candle.low(),
                        candle.close());

                /*
                 * Calcular High para comprobarlo.
                 */
                if (candle.high()
                        > calculatedWeeklyHigh) {

                    calculatedWeeklyHigh =
                            candle.high();

                    System.out.println(
                            "       >>> NEW WEEKLY HIGH = "
                                    + calculatedWeeklyHigh);
                }

                /*
                 * Friday Close.
                 */
                if (date.getDayOfWeek()
                        == DayOfWeek.FRIDAY) {

                    calculatedFridayClose =
                            candle.close();

                    System.out.println(
                            "       >>> FRIDAY CLOSE = "
                                    + calculatedFridayClose);
                }
            }
        }

        System.out.println();
        System.out.println(
                "W-1 calculated High : "
                        + calculatedWeeklyHigh);

        System.out.println(
                "W-1 reference High  : "
                        + reference.previousWeekHigh());

        System.out.println(
                "W-1 calculated Close: "
                        + calculatedFridayClose);

        System.out.println(
                "W-1 reference Close : "
                        + reference.lastFridayClose());

        /*
         * =============================================
         * W-2
         * =============================================
         */
        System.out.println();
        System.out.println(
                "------------------------------------------------------------");

        System.out.println(
                "W-2 CANDLES");

        System.out.println(
                "------------------------------------------------------------");

        long calculatedPreviousFridayClose =
                0L;

        for (int i = 0;
             i < currentIndex;
             i++) {

            Candle candle =
                    marketData.get(i);

            LocalDate date =
                    LocalDate.from(
                            candle.date());

            if (!date.isBefore(previousPreviousMonday)
                    && date.isBefore(previousMonday)) {

                System.out.printf(
                        "[%d] %s | O=%d H=%d L=%d C=%d%n",
                        i,
                        candle.date(),
                        candle.open(),
                        candle.high(),
                        candle.low(),
                        candle.close());

                if (date.equals(
                        previousPreviousMonday
                                .plusDays(4))) {

                    calculatedPreviousFridayClose =
                            candle.close();

                    System.out.println(
                            "       >>> W-2 FRIDAY CLOSE = "
                                    + calculatedPreviousFridayClose);
                }
            }
        }

        System.out.println();
        System.out.println(
                "W-2 calculated Close: "
                        + calculatedPreviousFridayClose);

        System.out.println(
                "W-2 reference Close : "
                        + reference.previousFridayClose());

        /*
         * =============================================
         * FÓRMULAS
         * =============================================
         */
        double rangePoints =
                reference.previousWeekHigh()
                        - reference.previousFridayClose();

        double mondayGap =
                (double) context.candle().open()
                        - reference.lastFridayClose();

        double calculatedTarget =
                reference.lastFridayClose()
                        + rangePoints;

        boolean entry =
                mondayGap < rangePoints;

        System.out.println();
        System.out.println(
                "------------------------------------------------------------");

        System.out.println(
                "CALCULATION");

        System.out.println(
                "------------------------------------------------------------");

        System.out.println(
                "Monday Open          = "
                        + context.candle().open());

        System.out.println(
                "Friday Close W-1     = "
                        + reference.lastFridayClose());

        System.out.println(
                "Weekly High W-1      = "
                        + reference.previousWeekHigh());

        System.out.println(
                "Friday Close W-2     = "
                        + reference.previousFridayClose());

        System.out.println();

        System.out.println(
                "RANGE:");

        System.out.println(
                "High(W-1) - Close(W-2)");

        System.out.println(
                reference.previousWeekHigh()
                        + " - "
                        + reference.previousFridayClose()
                        + " = "
                        + rangePoints);

        System.out.println();

        System.out.println(
                "MONDAY GAP:");

        System.out.println(
                "Open(Monday) - Close(W-1)");

        System.out.println(
                context.candle().open()
                        + " - "
                        + reference.lastFridayClose()
                        + " = "
                        + mondayGap);

        System.out.println();

        System.out.println(
                "ENTRY:");

        System.out.println(
                mondayGap
                        + " < "
                        + rangePoints
                        + " = "
                        + entry);

        System.out.println();

        System.out.println(
                "TARGET:");

        System.out.println(
                "Close(W-1) + RANGE");

        System.out.println(
                reference.lastFridayClose()
                        + " + "
                        + rangePoints
                        + " = "
                        + calculatedTarget);

        System.out.println(
                "============================================================");

        System.out.println();
    }

    private void buy(
            MarketContext context,
            Broker broker) {

        Candle candle =
                context.candle();

        double allocation =
                allocationForDrawdown(
                        context.drawdown());

        double price =
                candle.open() * 0.01;

        int shares =
                (int) (
                        broker.cash()
                                * allocation
                                / price);

        if (shares > 0) {

            broker.buy(
                    candle.date(),
                    candle.open(),
                    shares,
                    BuyType.LUNES);
        }
    }

    private double allocationForDrawdown(
            double drawdown) {

        return 1.00;
    }

    private boolean isMonday(
            Candle candle) {

        return candle.date()
                .getDayOfWeek()
                == DayOfWeek.MONDAY;
    }

    private boolean isLastDayOfWeek(
            MarketData marketData,
            int index) {

        if (index >= marketData.size() - 1) {
            return true;
        }

        LocalDate current =
                LocalDate.from(
                        marketData
                                .get(index)
                                .date());

        LocalDate next =
                LocalDate.from(
                        marketData
                                .get(index + 1)
                                .date());

        return current.getDayOfWeek()
                == DayOfWeek.FRIDAY
                || !current
                .plusDays(1)
                .equals(next);
    }

    /**
     * Referencias semanales utilizadas
     * para construir la operación.
     */
    private record WeeklyReference(
            double previousWeekHigh,
            long lastFridayClose,
            long previousFridayClose) {
    }
}