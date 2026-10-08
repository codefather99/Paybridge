package com.academy.paybridge.customer.web;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final RateLimiter limiter = new RateLimiter(now::get);

    @Test
    void allowsUpToTheLimitThenBlocks() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1)).allowed()).isTrue();
        }

        assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1)).allowed()).isFalse();
    }

    @Test
    void tellsTheCallerHowLongToWait() {
        limiter.tryAcquire("k", 1, Duration.ofMinutes(1));
        now.addAndGet(20_000);   // 20 seconds into the window

        RateLimiter.Decision blocked = limiter.tryAcquire("k", 1, Duration.ofMinutes(1));

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(40);
    }

    @Test
    void allowsAgainOnceTheWindowHasPassed() {
        limiter.tryAcquire("k", 1, Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1)).allowed()).isFalse();

        now.addAndGet(60_000);

        assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    void differentKeysDoNotShareACounter() {
        limiter.tryAcquire("ada", 1, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("ada", 1, Duration.ofMinutes(1)).allowed()).isFalse();
        assertThat(limiter.tryAcquire("bola", 1, Duration.ofMinutes(1)).allowed()).isTrue();
    }
}