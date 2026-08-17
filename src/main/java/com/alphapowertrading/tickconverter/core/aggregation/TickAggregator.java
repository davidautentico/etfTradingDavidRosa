package com.alphapowertrading.tickconverter.core.aggregation;

import com.alphapowertrading.tickconverter.core.model.Ohlc;
import com.alphapowertrading.tickconverter.core.model.Tick;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TickAggregator {

    public List<Ohlc> aggregate(
            List<Tick> ticks,
            int minutes
    ) {
        if (minutes <= 0) {
            throw new IllegalArgumentException(
                    "Frequency must be greater than zero"
            );
        }

        if (ticks.isEmpty()) {
            return List.of();
        }

        long intervalSeconds =
                Duration.ofMinutes(minutes).getSeconds();

        List<Tick> sorted = ticks.stream()
                .sorted(Comparator.comparing(Tick::timestamp))
                .toList();

        List<Ohlc> result = new ArrayList<>();

        Instant currentBucket = bucketStart(
                sorted.get(0).timestamp(),
                intervalSeconds
        );

        int open = 0;
        int high = Integer.MIN_VALUE;
        int low = Integer.MAX_VALUE;
        int close = 0;

        boolean initialized = false;

        for (Tick tick : sorted) {
            Instant bucket = bucketStart(
                    tick.timestamp(),
                    intervalSeconds
            );

            if (!bucket.equals(currentBucket)) {
                if (initialized) {
                    result.add(new Ohlc(
                            currentBucket,
                            open,
                            high,
                            low,
                            close
                    ));
                }

                currentBucket = bucket;
                high = Integer.MIN_VALUE;
                low = Integer.MAX_VALUE;
                initialized = false;
            }

            if (!initialized) {
                open = tick.price();
                initialized = true;
            }

            high = Math.max(high, tick.price());
            low = Math.min(low, tick.price());
            close = tick.price();
        }

        if (initialized) {
            result.add(new Ohlc(
                    currentBucket,
                    open,
                    high,
                    low,
                    close
            ));
        }

        return result;
    }

    private Instant bucketStart(
            Instant timestamp,
            long intervalSeconds
    ) {
        long seconds = timestamp.getEpochSecond();

        long bucketSeconds =
                Math.floorDiv(seconds, intervalSeconds)
                        * intervalSeconds;

        return Instant.ofEpochSecond(bucketSeconds);
    }
}