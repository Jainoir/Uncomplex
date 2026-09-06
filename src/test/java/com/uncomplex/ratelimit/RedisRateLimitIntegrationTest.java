package com.uncomplex.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the Redis-backed fixed-window limiter against real Redis. Skipped
 * automatically without Docker; runs in CI.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.rate-limit.store=redis",
        "app.rate-limit.generations-per-day=2",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RedisRateLimitIntegrationTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Test
    void luaCounterIsAtomicUnderConcurrentRequestsAndSetsExpiry() throws Exception {
        String prefix = "test:" + java.util.UUID.randomUUID() + ":";
        var limiter = new RedisRateLimiter(redisTemplate, 4, java.time.Duration.ofSeconds(30), prefix);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var tasks = java.util.stream.IntStream.range(0, 20)
                    .<java.util.concurrent.Callable<Boolean>>mapToObj(i -> () -> limiter.tryConsume("client").allowed())
                    .toList();
            int allowed = 0;
            for (var result : executor.invokeAll(tasks)) if (result.get()) allowed++;
            assertThat(allowed).isEqualTo(4);
            assertThat(redisTemplate.getExpire(prefix + "client")).isBetween(1L, 30L);
        }
    }

    @Test
    void luaRepairsMissingExpiryAndBudgetsRemainSeparate() {
        String prefix = "test:" + java.util.UUID.randomUUID() + ":";
        redisTemplate.opsForValue().set(prefix + "generation:client", "1");
        assertThat(redisTemplate.getExpire(prefix + "generation:client")).isEqualTo(-1);
        var generation = new RedisRateLimiter(redisTemplate, 1, java.time.Duration.ofSeconds(30), prefix + "generation:");
        var auth = new RedisRateLimiter(redisTemplate, 1, java.time.Duration.ofSeconds(30), prefix + "auth:");
        assertThat(generation.tryConsume("client").allowed()).isFalse();
        assertThat(redisTemplate.getExpire(prefix + "generation:client")).isBetween(1L, 30L);
        assertThat(auth.tryConsume("client").allowed()).isTrue();
    }

    @Test
    void luaWindowAllowsRequestsAgainAfterExpiry() throws Exception {
        String prefix = "test:" + java.util.UUID.randomUUID() + ":";
        var limiter = new RedisRateLimiter(redisTemplate, 1, java.time.Duration.ofSeconds(1), prefix);
        assertThat(limiter.tryConsume("client").allowed()).isTrue();
        assertThat(limiter.tryConsume("client").allowed()).isFalse();
        Thread.sleep(1200);
        assertThat(limiter.tryConsume("client").allowed()).isTrue();
    }

    @Test
    void redisLimiterIsSelectedAndEnforcesTheWindow() throws Exception {
        assertThat(rateLimiter).isInstanceOf(RedisRateLimiter.class);

        String body = """
                {"topic":"Redis limits %d","experienceLevel":"BEGINNER","goal":"GENERAL_UNDERSTANDING"}
                """;

        mockMvc.perform(post("/api/roadmaps").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(1)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-RateLimit-Remaining", "1"));

        mockMvc.perform(post("/api/roadmaps").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(2)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));

        mockMvc.perform(post("/api/roadmaps").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(3)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
