package com.alphapowertrading.tickchecker.model;

import java.time.Instant;

public record Tick(Instant timestamp, int price) {}
