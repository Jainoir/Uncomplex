package com.uncomplex.roadmap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack test against H2 (PostgreSQL mode) with the mock AI generator:
 * generate -> share -> open shared link, plus validation and 404 paths.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoadmapControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generateThenOpenSharedLinkWithoutRegenerating() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/roadmaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic":"Rate limiting","experienceLevel":"BEGINNER","goal":"SYSTEM_DESIGN_INTERVIEW"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.prerequisites.length()").value(5))
                .andExpect(jsonPath("$.prerequisites[0].position").value(1))
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        String shareToken = body.get("shareToken").asText();
        assertThat(shareToken).matches("rate-limiting-[a-z2-9]{5}");

        // Anyone can open the shared link without an account
        mockMvc.perform(get("/api/roadmaps/public/{token}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareToken").value(shareToken))
                .andExpect(jsonPath("$.prerequisites.length()").value(5));

        // The same topic/level/goal is served from the database, not regenerated
        mockMvc.perform(post("/api/roadmaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic":"  RATE   LIMITING ","experienceLevel":"BEGINNER","goal":"SYSTEM_DESIGN_INTERVIEW"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shareToken").value(shareToken));
    }

    @Test
    void unknownShareTokenReturns404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/roadmaps/public/{token}", "does-not-exist-xxxxx"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not found"));
    }

    @Test
    void blankTopicIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/roadmaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic":"   ","experienceLevel":"BEGINNER","goal":"BUILD_A_PROJECT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void invalidEnumValueIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/roadmaps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic":"Docker","experienceLevel":"WIZARD","goal":"BUILD_A_PROJECT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
    }
}
