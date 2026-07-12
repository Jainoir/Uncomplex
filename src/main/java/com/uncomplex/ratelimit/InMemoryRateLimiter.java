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
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(long capacityPerDay) {
        this.capacityPerDay = capacityPerDay;
    }

    @Override
    public Decision tryConsume(String clientKey) {
        Bucket bucket = buckets.computeIfAbsent(clientKey, key -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        long retryAfter = Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds();
        return new Decision(probe.isConsumed(), probe.getRemainingTokens(), retryAfter);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacityPerDay)
                .refillIntervally(capacityPerDay, Duration.ofDays(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
