package com.academy.paybridge.customer.web;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Fixed-window counters held in memory. Correct for one instance; use a shared store for several. */
class RateLimiter {

    record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private record Window(long startMillis, long lengthMillis, int count) {
    }

    private static final int PRUNE_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final LongSupplier nowMillis;

    RateLimiter(LongSupplier nowMillis) {
        this.nowMillis = nowMillis;
    }

    Decision tryAcquire(String key, int limit, Duration window) {
        long now = nowMillis.getAsLong();
        long windowMillis = window.toMillis();
        Decision[] outcome = new Decision[1];

        // compute() runs atomically per key, so concurrent requests cannot both slip past the limit.
        windows.compute(key, (k, current) -> {
            if (current == null || now - current.startMillis() >= current.lengthMillis()) {
                outcome[0] = new Decision(true, 0);
                return new Window(now, windowMillis, 1);
            }
            if (current.count() < limit) {
                outcome[0] = new Decision(true, 0);
                return new Window(current.startMillis(), current.lengthMillis(), current.count() + 1);
            }
            long remainingMillis = current.lengthMillis() - (now - current.startMillis());
            outcome[0] = new Decision(false, Math.max(1, (remainingMillis + 999) / 1000));
            return current;
        });

        if (windows.size() > PRUNE_THRESHOLD) {
            // Stop the map growing forever when many different clients each call once.
            windows.entrySet().removeIf(e -> now - e.getValue().startMillis() >= e.getValue().lengthMillis());
        }
        return outcome[0];
    }
}