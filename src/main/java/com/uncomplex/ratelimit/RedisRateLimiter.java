package com.uncomplex.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.time.Duration;
import java.util.List;

/** Atomic counter and expiry, shared across replicas. */
public class RedisRateLimiter implements RateLimiter {
    private static final DefaultRedisScript<List> CONSUME = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('TTL', KEYS[1])
            if ttl < 0 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
                ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);
    private final StringRedisTemplate redis;
    private final long capacityPerWindow;
    private final Duration window;
    private final String prefix;

    public RedisRateLimiter(StringRedisTemplate redis, long capacityPerWindow) {
        this(redis, capacityPerWindow, Duration.ofDays(1), "ratelimit:generate:");
    }

    public RedisRateLimiter(StringRedisTemplate redis, long capacityPerWindow, Duration window, String prefix) {
        this.redis = redis;
        this.capacityPerWindow = capacityPerWindow;
        this.window = window;
        this.prefix = prefix;
    }

    @Override
    public Decision tryConsume(String clientKey) {
        List<?> result = redis.execute(CONSUME, List.of(prefix + clientKey), String.valueOf(window.toSeconds()));
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Rate limit counter unavailable");
        }
        long count = ((Number) result.get(0)).longValue();
        long ttl = ((Number) result.get(1)).longValue();
        return new Decision(count <= capacityPerWindow, Math.max(0, capacityPerWindow - count), Math.max(1, ttl));
    }
}