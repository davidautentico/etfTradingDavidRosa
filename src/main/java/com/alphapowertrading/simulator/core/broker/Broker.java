package com.alphapowertrading.simulator.core.broker;

import com.alphapowertrading.simulator.core.report.BacktestReport;

import java.time.LocalDate;
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

    private LocalDate startDate;

    public Broker(double initialCapital, boolean showTrades) {
        this(initialCapital, showTrades, 0.0);
    }

    public Broker(double initialCapital, boolean showTrades, double commissionRate) {
        if (initialCapital <= 0) throw new IllegalArgumentException("Initial capital must be greater than zero");
        if (commissionRate < 0) throw new IllegalArgumentException("Commission rate cannot be negative");

        this.initialCapital = initialCapital;
        this.cash = initialCapital;
        this.peakEquity = initialCapital;
        this.showTrades = showTrades;
        this.commissionRate = commissionRate;
    }

    public void buy(LocalDate date, long price, int quantity, BuyType buyType) {
        if (position != null) throw new IllegalStateException("A position is already open");
        if (price <= 0 || quantity <= 0) throw new IllegalArgumentException("Price and quantity must be greater than zero");
        if (buyType == null) throw new IllegalArgumentException("Buy type cannot be null");

        registerStartDate(date);

        double adjustedPrice = price * 0.01;
        double costPerShare = adjustedPrice * (1 + commissionRate);
        int actualQuantity = Math.min(quantity, (int) Math.floor(cash / costPerShare));

        if (actualQuantity <= 0) return;

        double cost = adjustedPrice * actualQuantity;
        double commission = cost * commissionRate;

        cash -= cost + commission;

        //System.out.println("coste y comision: "+ cost + " "+commission);
        position = new Position(
                date,
                price,
                actualQuantity,
                buyType
        );
    }

    public void buy(LocalDate date, long price, int quantity) {
        buy(date, price, quantity, BuyType.NO_LUNES);
    }

    public void sell(LocalDate date, long price, String closeReason) {
        if (position == null) return;
        if (price <= 0) throw new IllegalArgumentException("Price must be greater than zero");

        double adjustedPrice = price * 0.01;
        double entryPrice = position.entryPrice() * 0.01;
        double entryValue = entryPrice * position.quantity();
        double proceeds = adjustedPrice * position.quantity();
        double buyCommission = entryValue * commissionRate;
        double sellCommission = proceeds * commissionRate;

        double profit = proceeds - entryValue - buyCommission - sellCommission;
        double pnlPercentage = ((double) price / position.entryPrice() - 1) * 100;

        cash += proceeds - sellCommission;

        Trade trade = new Trade(
                position.entryDate(),
                date,
                position.entryPrice(),
                price,
                position.quantity(),
                profit,
                closeReason,
                position.buyType()
        );

        trades.add(trade);
        position = null;

        if (showTrades) {
            System.out.printf(
                    "Closed | %s | %s | %s -> %s | PnL: %.2f (%.2f%%) | Commission: %.2f | Cash: %.2f%n",
                    trade.buyType(), trade.closeReason(), trade.entryDate(), trade.exitDate(),
                    trade.profit(), pnlPercentage, buyCommission + sellCommission, cash
            );
        }
    }

    public void sell(LocalDate date, long price) {
        sell(date, price, "MANUAL");
    }

    public void recordEquity(long closePrice) {
        equityCurve.add(equity(closePrice));
    }

    public List<Double> equityCurve() {
        return List.copyOf(equityCurve);
    }

    public boolean updateDrawdown(LocalDate date, long lowPrice) {
        registerStartDate(date);

        double currentEquity = equity(lowPrice);

        if (currentEquity > peakEquity) peakEquity = currentEquity;

        double drawdown = (currentEquity - peakEquity) / peakEquity;

        if (drawdown >= 0) return false;

        drawdownSum += drawdown;
        drawdownObservations++;

        if (drawdown < maxDrawdown) {
            maxDrawdown = drawdown;
            return true;
        }

        return false;
    }

    private void registerStartDate(LocalDate date) {
        if (startDate == null) startDate = date;
    }

    public boolean hasOpenPosition() { return position != null; }
    public Position position() { return position; }
    public double cash() { return cash; }
    public double initialCapital() { return initialCapital; }
    public double commissionRate() { return commissionRate; }
    public double peakEquity() { return peakEquity; }
    public double maxDrawdown() { return maxDrawdown; }
    public List<Trade> trades() { return List.copyOf(trades); }

    public double equity(long currentPrice) {
        if (position == null) return cash;
        return cash + position.quantity() * currentPrice * 0.01;
    }

    public double averageDrawdown() {
        if (drawdownObservations == 0) return 0;
        return drawdownSum / drawdownObservations;
    }

    public double cagr(double finalEquity, LocalDate finalDate) {
        if (startDate == null) return 0;

        long days = ChronoUnit.DAYS.between(startDate, finalDate);
        if (days <= 0) return 0;

        double years = days / 365.25;
        return Math.pow(finalEquity / initialCapital, 1 / years) - 1;
    }

    public BacktestReport buildReport(long finalPrice, LocalDate finalDate) {
        double finalEquity = equity(finalPrice);

        return new BacktestReport(
                cash, finalEquity, trades, equityCurve,
                averageDrawdown(), maxDrawdown, cagr(finalEquity, finalDate)
        );
    }
}
