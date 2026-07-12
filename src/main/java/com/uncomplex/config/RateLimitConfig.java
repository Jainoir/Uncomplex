package com.uncomplex.config;

import com.uncomplex.ratelimit.InMemoryRateLimiter;
import com.uncomplex.ratelimit.RateLimiter;
import com.uncomplex.ratelimit.RedisRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "redis")
    public RateLimiter redisRateLimiter(StringRedisTemplate redisTemplate, AppProperties properties) {
        log.info("Rate limiting: Redis fixed window ({} generations/day, shared across replicas)",
                properties.rateLimit().generationsPerDay());
        return new RedisRateLimiter(redisTemplate, properties.rateLimit().generationsPerDay());
    }

    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter inMemoryRateLimiter(AppProperties properties) {
        log.info("Rate limiting: in-memory token bucket ({} generations/day, single instance)",
                properties.rateLimit().generationsPerDay());
        return new InMemoryRateLimiter(properties.rateLimit().generationsPerDay());
    }
}
