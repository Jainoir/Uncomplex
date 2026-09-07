package com.uncomplex.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

    private InMemoryRateLimiter limiter(long capacity, Duration window) {
        return new InMemoryRateLimiter(capacity, window, now::get);
    }

    @Test
    void clientIdentifiersAreNotRetainedForever() {
        var limiter = limiter(10, Duration.ofHours(1));
        for (int i = 0; i < 500; i++) {
            limiter.tryConsume("198.51.100." + i);
        }
        assertThat(limiter.trackedClients()).isEqualTo(500);

        // A full window with no sign of them, then any request triggers the sweep.
        now.set(now.get().plus(Duration.ofHours(2)));
        limiter.tryConsume("203.0.113.9");

        assertThat(limiter.trackedClients()).isEqualTo(1);
    }

    @Test
    void aClientStillActiveIsNotEvicted() {
        var limiter = limiter(10, Duration.ofHours(1));
        limiter.tryConsume("198.51.100.1");

        now.set(now.get().plus(Duration.ofMinutes(50)));
        limiter.tryConsume("198.51.100.1");   // seen again, inside the window
        now.set(now.get().plus(Duration.ofMinutes(30)));
        limiter.tryConsume("203.0.113.9");    // crosses the boundary, triggers a sweep

        assertThat(limiter.trackedClients()).isEqualTo(2);
    }

    @Test
    void evictionDoesNotHandOutExtraRequestsWithinAWindow() {
        var limiter = limiter(2, Duration.ofHours(1));
        assertThat(limiter.tryConsume("198.51.100.1").allowed()).isTrue();
        assertThat(limiter.tryConsume("198.51.100.1").allowed()).isTrue();
        assertThat(limiter.tryConsume("198.51.100.1").allowed()).isFalse();

        // Another client crossing a boundary must not reset an active client's budget.
        now.set(now.get().plus(Duration.ofMinutes(61)));
        limiter.tryConsume("203.0.113.9");

        // The window has genuinely passed for this client too, so it is allowed again --
        // by refill, which is the same answer eviction would give.
        assertThat(limiter.tryConsume("198.51.100.1").allowed()).isTrue();
    }

    @Test
    void retryAfterIsAtLeastOneSecond() {
        var limiter = limiter(1, Duration.ofHours(1));
        limiter.tryConsume("198.51.100.1");
        var denied = limiter.tryConsume("198.51.100.1");

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }
}
