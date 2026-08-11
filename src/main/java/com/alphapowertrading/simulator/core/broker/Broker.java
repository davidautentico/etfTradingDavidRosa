package com.alphapowertrading.simulator.core.broker;

import com.alphapowertrading.simulator.core.report.BacktestReport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Broker {

    private final List<Trade> trades = new ArrayList<>();
    private double cash;
    private Position position;

    public Broker(double initialCapital) {
        if (initialCapital <= 0) {
            throw new IllegalArgumentException("Initial capital must be greater than zero");
        }
        this.cash = initialCapital;
    }

    public void buy(LocalDate date, long price, int quantity) {
        if (position != null) {
            throw new IllegalStateException("A position is already open");
        }
        if (price <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("Price and quantity must be greater than zero");
        }

        double cost = price * quantity;
        if (cost > cash) {
            throw new IllegalStateException("Insufficient cash");
        }

        cash -= cost;
        position = new Position(date, price, quantity);
    }

    public void sell(LocalDate date, long price) {
        if (position == null) {
            throw new IllegalStateException("No open position");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }

        double proceeds = (double) price * position.quantity();
        double profit = (double) (price - position.entryPrice()) * position.quantity();

        cash += proceeds;
        trades.add(new Trade(
                position.entryDate(),
                date,
                position.entryPrice(),
                price,
                position.quantity(),
                profit
        ));
        position = null;
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

    public List<Trade> trades() {
        return List.copyOf(trades);
    }

    public double equity(long currentPrice) {
        if (position == null) {
            return cash;
        }
        return cash + (double) position.quantity() * currentPrice;
    }

    public BacktestReport buildReport(long finalPrice) {
        return new BacktestReport(cash, equity(finalPrice), trades);
    }
}
