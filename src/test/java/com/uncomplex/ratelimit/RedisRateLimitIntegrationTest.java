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
