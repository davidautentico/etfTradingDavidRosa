package com.alphapowertrading.simulator.analytics.weekly;

import java.time.LocalDate;

record WeeklyCandle(LocalDate date, double open, double high, double low, double close) {}
