package com.uncomplex.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bucket4j token bucket per client, held in process memory. */
public class InMemoryRateLimiter implements RateLimiter {

    private final long capacityPerDay;
    private final Duration window;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(long capacityPerDay) {
        this(capacityPerDay, Duration.ofDays(1));
    }

    public InMemoryRateLimiter(long capacityPerDay, Duration window) {
        this.window = window;
        this.capacityPerDay = capacityPerDay;
    }

    @Override
    public Decision tryConsume(String clientKey) {
        Bucket bucket = buckets.computeIfAbsent(clientKey, key -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        long retryAfter = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
        return new Decision(probe.isConsumed(), probe.getRemainingTokens(), retryAfter);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacityPerDay)
                .refillIntervally(capacityPerDay, window)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
