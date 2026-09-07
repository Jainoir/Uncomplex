package com.uncomplex.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Bucket4j token bucket per client, held in process memory.
 *
 * Entries are evicted once a full window has passed since the client was last seen. Without
 * that the map only ever grew: every distinct IP was retained for the lifetime of the
 * process, which both leaked memory across unique visitors and made the privacy policy's
 * claim that counters expire untrue. A bucket idle for a whole window has refilled to
 * capacity anyway, so dropping it is indistinguishable from keeping it.
 */
public class InMemoryRateLimiter implements RateLimiter {

    private final long capacityPerWindow;
    private final Duration window;
    private final Supplier<Instant> clock;
    private final Map<String, Entry> buckets = new ConcurrentHashMap<>();
    private final AtomicLong nextSweep = new AtomicLong();

    public InMemoryRateLimiter(long capacityPerWindow) {
        this(capacityPerWindow, Duration.ofDays(1));
    }

    public InMemoryRateLimiter(long capacityPerWindow, Duration window) {
        this(capacityPerWindow, window, Instant::now);
    }

    InMemoryRateLimiter(long capacityPerWindow, Duration window, Supplier<Instant> clock) {
        this.capacityPerWindow = capacityPerWindow;
        this.window = window;
        this.clock = clock;
        this.nextSweep.set(clock.get().toEpochMilli() + window.toMillis());
    }

    @Override
    public Decision tryConsume(String clientKey) {
        Instant now = clock.get();
        sweepIfDue(now);

        Entry entry = buckets.compute(clientKey, (key, existing) -> {
            Entry value = existing == null ? new Entry(newBucket()) : existing;
            value.lastSeen = now;
            return value;
        });

        ConsumptionProbe probe = entry.bucket.tryConsumeAndReturnRemaining(1);
        long retryAfter = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
        return new Decision(probe.isConsumed(), probe.getRemainingTokens(), retryAfter);
    }

    /**
     * Swept inline rather than on a timer: one pass per window, on whichever request happens
     * to cross the boundary, so an idle process schedules nothing and holds no thread. The
     * consequence is that cleanup is deferred — an entry that becomes eligible while no
     * requests are arriving waits until traffic resumes.
     *
     * Each key is rechecked and removed inside computeIfPresent, which takes the same per-key
     * lock as tryConsume's compute, so "is it expired" and "remove it" cannot straddle a
     * concurrent refresh. entrySet().removeIf cannot do this: it guards removal on value
     * identity via replaceNode(k, null, v), and Entry is mutated in place, so an entry
     * refreshed mid-sweep still matches the captured value and is dropped anyway. The client
     * then gets a brand-new full bucket and an extra allowance inside its own window.
     */
    private void sweepIfDue(Instant now) {
        long due = nextSweep.get();
        if (now.toEpochMilli() < due) return;
        if (!nextSweep.compareAndSet(due, now.toEpochMilli() + window.toMillis())) return;
        Instant cutoff = now.minus(window);
        for (String key : buckets.keySet()) {
            buckets.computeIfPresent(key, (k, entry) -> entry.lastSeen.isBefore(cutoff) ? null : entry);
        }
    }

    /** Visible for tests: how many client identifiers are currently retained. */
    int trackedClients() {
        return buckets.size();
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacityPerWindow)
                .refillIntervally(capacityPerWindow, window)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private static final class Entry {
        final Bucket bucket;
        volatile Instant lastSeen;

        Entry(Bucket bucket) {
            this.bucket = bucket;
        }
    }
}
