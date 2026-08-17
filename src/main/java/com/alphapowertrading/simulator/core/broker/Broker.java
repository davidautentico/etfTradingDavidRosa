package com.alphapowertrading.simulator.core.broker;

import com.alphapowertrading.simulator.core.report.BacktestReport;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class Broker {

    private final List<Trade> trades = new ArrayList<>();
    private final List<Double> equityCurve = new ArrayList<>();

    private final double initialCapital;
    private final boolean showTrades;
    private final double commissionRate;

    private double cash;
    private Position position;

    private double peakEquity;
    private double maxDrawdown;
    private double drawdownSum;
    private long drawdownObservations;

    private LocalDateTime startDate;

    public Broker(double initialCapital, boolean showTrades) {
        this(initialCapital, showTrades, 0.0);
    }

    public Broker(
            double initialCapital,
            boolean showTrades,
            double commissionRate
    ) {
        if (initialCapital <= 0) {
            throw new IllegalArgumentException(
                    "Initial capital must be greater than zero"
            );
        }

        if (commissionRate < 0) {
            throw new IllegalArgumentException(
                    "Commission rate cannot be negative"
            );
        }

        this.initialCapital = initialCapital;
        this.cash = initialCapital;
        this.peakEquity = initialCapital;
        this.showTrades = showTrades;
        this.commissionRate = commissionRate;
    }

    public void buy(
            LocalDateTime date,
            long price,
            int quantity,
            BuyType buyType
    ) {
        ensureNoOpenPosition();
        validateOrder(price, quantity, buyType);
        registerStartDate(date);

        double adjustedPrice = price * 0.01;
        double costPerShare = adjustedPrice * (1 + commissionRate);

        int actualQuantity = Math.min(
                quantity,
                (int) Math.floor(cash / costPerShare)
        );

        if (actualQuantity <= 0) {
            return;
        }

        double cost = adjustedPrice * actualQuantity;
        double commission = cost * commissionRate;

        cash -= cost + commission;

        position = new Position(
                date,
                price,
                actualQuantity,
                buyType,
                PositionSide.LONG
        );
    }

    public void buy(LocalDateTime date, long price, int quantity) {
        buy(date, price, quantity, BuyType.NO_LUNES);
    }

    public void shortSell(
            LocalDateTime date,
            long price,
            int quantity,
            BuyType buyType
    ) {
        ensureNoOpenPosition();
        validateOrder(price, quantity, buyType);
        registerStartDate(date);

        double adjustedPrice = price * 0.01;
        double proceeds = adjustedPrice * quantity;
        double commission = proceeds * commissionRate;

        cash += proceeds - commission;

        position = new Position(
                date,
                price,
                quantity,
                buyType,
                PositionSide.SHORT
        );
    }

    public void shortSell(LocalDateTime date, long price, int quantity) {
        shortSell(date, price, quantity, BuyType.NO_LUNES);
    }

    public void sell(
            LocalDateTime date,
            long price,
            String closeReason
    ) {
        if (position == null) {
            return;
        }

        if (position.side() != PositionSide.LONG) {
            throw new IllegalStateException(
                    "Current position is SHORT. Use buyToCover() to close it."
            );
        }

        validatePrice(price);

        double adjustedPrice = price * 0.01;
        double entryPrice = position.entryPrice() * 0.01;
        double entryValue = entryPrice * position.quantity();
        double proceeds = adjustedPrice * position.quantity();

        double buyCommission = entryValue * commissionRate;
        double sellCommission = proceeds * commissionRate;

        double profit = proceeds
                - entryValue
                - buyCommission
                - sellCommission;

        double pnlPercentage =
                ((double) price / position.entryPrice() - 1) * 100;

        cash += proceeds - sellCommission;

        Trade trade = new Trade(
                position.entryDate(),
                date,
                position.entryPrice(),
                price,
                position.quantity(),
                profit,
                closeReason,
                position.buyType(),
                PositionSide.LONG
        );

        trades.add(trade);
        position = null;

        printClosedTrade(
                trade,
                pnlPercentage,
                buyCommission + sellCommission
        );
    }

    public void sell(LocalDateTime date, long price) {
        sell(date, price, "MANUAL");
    }

    public void buyToCover(
            LocalDateTime date,
            long price,
            String closeReason
    ) {
        if (position == null) {
            return;
        }

        if (position.side() != PositionSide.SHORT) {
            throw new IllegalStateException(
                    "Current position is LONG. Use sell() to close it."
            );
        }

        validatePrice(price);

        double adjustedEntryPrice = position.entryPrice() * 0.01;
        double adjustedExitPrice = price * 0.01;

        double entryValue =
                adjustedEntryPrice * position.quantity();
        double exitValue =
                adjustedExitPrice * position.quantity();

        double sellCommission = entryValue * commissionRate;
        double buyCommission = exitValue * commissionRate;

        double profit = entryValue
                - exitValue
                - sellCommission
                - buyCommission;

        double pnlPercentage =
                ((double) position.entryPrice() / price - 1) * 100;

        cash -= exitValue + buyCommission;

        Trade trade = new Trade(
                position.entryDate(),
                date,
                position.entryPrice(),
                price,
                position.quantity(),
                profit,
                closeReason,
                position.buyType(),
                PositionSide.SHORT
        );

        trades.add(trade);
        position = null;

        printClosedTrade(
                trade,
                pnlPercentage,
                sellCommission + buyCommission
        );
    }

    public void buyToCover(LocalDateTime date, long price) {
        buyToCover(date, price, "MANUAL");
    }

    private void printClosedTrade(
            Trade trade,
            double pnlPercentage,
            double commission
    ) {
        if (!showTrades) {
            return;
        }

        System.out.printf(
                "Closed %s | %s | %s | %s -> %s | "
                        + "PnL: %.2f (%.2f%%) | Commission: %.2f | "
                        + "Cash: %.2f%n",
                trade.side(),
                trade.buyType(),
                trade.closeReason(),
                trade.entryDate(),
                trade.exitDate(),
                trade.profit(),
                pnlPercentage,
                commission,
                cash
        );
    }

    private void ensureNoOpenPosition() {
        if (position != null) {
            throw new IllegalStateException("A position is already open");
        }
    }

    private void validateOrder(
            long price,
            int quantity,
            BuyType buyType
    ) {
        if (price <= 0 || quantity <= 0) {
            throw new IllegalArgumentException(
                    "Price and quantity must be greater than zero"
            );
        }

        if (buyType == null) {
            throw new IllegalArgumentException("Buy type cannot be null");
        }
    }

    private void validatePrice(long price) {
        if (price <= 0) {
            throw new IllegalArgumentException(
                    "Price must be greater than zero"
            );
        }
    }

    public void recordEquity(long closePrice) {
        equityCurve.add(equity(closePrice));
    }

    public List<Double> equityCurve() {
        return List.copyOf(equityCurve);
    }

    public boolean updateDrawdown(
            LocalDateTime date,
            long lowPrice
    ) {
        registerStartDate(date);

        double currentEquity = equity(lowPrice);

        if (currentEquity > peakEquity) {
            peakEquity = currentEquity;
        }

        double drawdown = (currentEquity - peakEquity) / peakEquity;

        if (drawdown >= 0) {
            return false;
        }

        drawdownSum += drawdown;
        drawdownObservations++;

        if (drawdown < maxDrawdown) {
            maxDrawdown = drawdown;
            return true;
        }

        return false;
    }

    private void registerStartDate(LocalDateTime date) {
        if (startDate == null) {
            startDate = date;
        }
    }

    public boolean hasOpenPosition() {
        return position != null;
    }

    public Position position() {
        return position;
    }

    public double cash() {
        return cash;
    }

    public double initialCapital() {
        return initialCapital;
    }

    public double commissionRate() {
        return commissionRate;
    }

    public double peakEquity() {
        return peakEquity;
    }

    public double maxDrawdown() {
        return maxDrawdown;
    }

    public List<Trade> trades() {
        return List.copyOf(trades);
    }

    public double equity(long currentPrice) {
        if (position == null) {
            return cash;
        }

        double currentValue = currentPrice * 0.01;
        double entryValue = position.entryPrice() * 0.01;
        double quantity = position.quantity();

        if (position.side() == PositionSide.LONG) {
            return cash + quantity * currentValue;
        }

        double unrealizedProfit =
                quantity * (entryValue - currentValue);

        return cash + unrealizedProfit;
    }

    public double averageDrawdown() {
        if (drawdownObservations == 0) {
            return 0;
        }

        return drawdownSum / drawdownObservations;
    }

    public double cagr(
            double finalEquity,
            LocalDateTime finalDate
    ) {
        if (startDate == null) {
            return 0;
        }

        long seconds = ChronoUnit.SECONDS.between(
                startDate,
                finalDate
        );

        if (seconds <= 0) {
            return 0;
        }

        double years =
                seconds / (365.25 * 24 * 60 * 60);

        return Math.pow(
                finalEquity / initialCapital,
                1 / years
        ) - 1;
    }

    public BacktestReport buildReport(
            long finalPrice,
            LocalDateTime finalDate
    ) {
        double finalEquity = equity(finalPrice);

        return new BacktestReport(
                cash,
                finalEquity,
                trades,
                equityCurve,
                averageDrawdown(),
                maxDrawdown,
                cagr(finalEquity, finalDate)
        );
    }
}
