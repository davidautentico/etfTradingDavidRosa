package com.alphapowertrading.statistical.model;

import java.time.LocalDateTime;

public record Ohlc(
    LocalDateTime timestamp,
    int open,
    int high,
    int low,
    int close) {}
