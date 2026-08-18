package com.alphapowertrading.tickconverter.core.aggregation;

import com.alphapowertrading.tickconverter.core.model.Ohlc;
import com.alphapowertrading.tickconverter.core.model.Tick;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Aggregates BID ticks into fixed time buckets.
 *
 * <p>Buckets are aligned to the clock. Empty buckets are generated using the
 * previous close.
 */
public class BidAskAggregator {

    private final long intervalSeconds;
    private final Deque<Ohlc> completed = new ArrayDeque<>();

    private Instant currentBucket;
    private int open;
    private int high;
    private int low;
    private int close;
    private boolean initialized;

    public BidAskAggregator(int minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Frequency must be greater than zero");
        }

        intervalSeconds = Duration.ofMinutes(minutes).getSeconds();
    }

    public void add(Tick tick) {
        Instant bucket = bucketStart(tick.timestamp());

        if (!initialized) {
            startBucket(bucket, tick.price());
            return;
        }

        if (bucket.equals(currentBucket)) {
            high = Math.max(high, tick.price());
            low = Math.min(low, tick.price());
            close = tick.price();
            return;
        }

        completeCurrentBucket();

        int previousClose = close;
        Instant nextBucket = currentBucket.plusSeconds(intervalSeconds);

        while (nextBucket.isBefore(bucket)) {
            completed.add(
                    new Ohlc(
                            nextBucket,
                            previousClose,
                            previousClose,
                            previousClose,
                            previousClose));

            nextBucket = nextBucket.plusSeconds(intervalSeconds);
        }

        startBucket(bucket, tick.price());
    }

    public Ohlc pollCompleted() {
        return completed.pollFirst();
    }

    public void restoreCompleted(Ohlc candle) {
        if (candle != null) {
            completed.addFirst(candle);
        }
    }

    public Ohlc flush() {
        if (!initialized) {
            return completed.pollFirst();
        }

        completeCurrentBucket();

        currentBucket = null;
        initialized = false;

        return completed.pollFirst();
    }

    private void completeCurrentBucket() {
        completed.add(
                new Ohlc(
                        currentBucket,
                        open,
                        high,
                        low,
                        close));
    }

    private void startBucket(Instant bucket, int price) {
        currentBucket = bucket;
        open = price;
        high = price;
        low = price;
        close = price;
        initialized = true;
    }

    private Instant bucketStart(Instant timestamp) {
        long epochSeconds = timestamp.getEpochSecond();

        long bucketSeconds =
                Math.floorDiv(epochSeconds, intervalSeconds) * intervalSeconds;

        return Instant.ofEpochSecond(bucketSeconds);
    }
}