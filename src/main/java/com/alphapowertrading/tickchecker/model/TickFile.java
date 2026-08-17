package com.alphapowertrading.tickchecker.model;

import java.nio.file.Path;
import java.time.LocalDate;

public record TickFile(
    Path path,
    Side side,
    LocalDate date,
    int hour) {}
