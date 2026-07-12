package com.uncomplex.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Fixed-window counter in Redis (atomic INCR + first-write EXPIRE), shared across
 * all application replicas. Deliberate trade-off vs the in-memory token bucket:
 * a fixed window allows a brief burst at the window boundary, but keeps the hot
 * path to a single round trip and needs no Lua or distributed locking.
 */
public class RedisRateLimiter implements RateLimiter {

    private static final Duration WINDOW = Duration.ofDays(1);
    private static final String KEY_PREFIX = "ratelimit:generate:";

    private final StringRedisTemplate redis;
    private final long capacityPerWindow;

    public RedisRateLimiter(StringRedisTemplate redis, long capacityPerWindow) {
        this.redis = redis;
        this.capacityPerWindow = capacityPerWindow;
    }

    @Override
    public Decision tryConsume(String clientKey) {
        String key = KEY_PREFIX + clientKey;
        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            // Defensive: treat an unreadable counter as allowed rather than locking users out.
            return new Decision(true, 0, 0);
        }
        if (count == 1) {
            redis.expire(key, WINDOW);
        }
        if (count <= capacityPerWindow) {
            return new Decision(true, capacityPerWindow - count, 0);
        }
        Long ttl = redis.getExpire(key);
        long retryAfter = (ttl == null || ttl < 0) ? WINDOW.toSeconds() : ttl;
        return new Decision(false, 0, retryAfter);
    }
}
