package com.alphapowertrading.correlation.model;

import java.time.LocalDateTime;

public record TradeRecord(LocalDateTime entryDate, LocalDateTime exitDate, double profitPercentage) {}
