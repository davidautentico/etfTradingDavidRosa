package com.alphapowertrading.tickchecker.model;

import java.time.Instant;

public record Ohlc(
    Instant timestamp,
    int open,
    int high,
    int low,
    int close) {}
