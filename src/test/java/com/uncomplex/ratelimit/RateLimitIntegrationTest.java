package com.uncomplex.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.rate-limit.generations-per-day=2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generationIsRateLimitedPerClient() throws Exception {
        String body = """
                {"topic":"Kubernetes %d","experienceLevel":"BEGINNER","goal":"GENERAL_UNDERSTANDING"}
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

        // Reads are never rate limited
        mockMvc.perform(get("/api/roadmaps/public/anything-zzzzz"))
                .andExpect(status().isNotFound());
    }
}
