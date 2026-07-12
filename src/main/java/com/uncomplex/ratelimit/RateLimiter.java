package com.uncomplex.ratelimit;

/**
 * Rate-limiting strategy. Two implementations: an in-memory token bucket for
 * single-instance deployments and a Redis fixed window shared across replicas
 * (selected via app.rate-limit.store).
 */
public interface RateLimiter {

    Decision tryConsume(String clientKey);

    record Decision(boolean allowed, long remaining, long retryAfterSeconds) {
    }
}
