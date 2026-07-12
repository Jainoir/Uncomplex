package com.uncomplex.library;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 2 end-to-end: register/login, auto-save on authenticated generation,
 * library listing, per-node progress, saving someone else's shared roadmap, and
 * removal. Runs against H2 with the mock AI generator.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthAndLibraryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    void registerLoginAndRejectionPaths() throws Exception {
        // Register
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"correct-horse-battery"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("alice@example.com"));

        // Duplicate email -> 409
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ALICE@example.com","password":"another-password-1"}
                                """))
                .andExpect(status().isConflict());

        // Weak password -> 400
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bob@example.com","password":"short"}
                                """))
                .andExpect(status().isBadRequest());

        // Wrong password -> 401, same message as unknown email (no account enumeration)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"wrong-password-123"}
                                """))
                .andExpect(status().isUnauthorized());

        // Correct login
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"correct-horse-battery"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @Order(2)
    void protectedEndpointsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/me/roadmaps"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    void fullLibraryAndProgressJourney() throws Exception {
        String aliceToken = registerAndLogin("journey@example.com");

        // Authenticated generation auto-saves to the library
        MvcResult generated = mockMvc.perform(post("/api/roadmaps")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic":"OAuth","experienceLevel":"BEGINNER","goal":"BUILD_A_PROJECT"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode roadmap = objectMapper.readTree(generated.getResponse().getContentAsString());
        long roadmapId = roadmap.get("id").asLong();
        long firstNodeId = roadmap.get("prerequisites").get(0).get("id").asLong();
        String shareToken = roadmap.get("shareToken").asText();

        // It shows up in "my roadmaps" with zero progress out of 5 nodes
        mockMvc.perform(get("/api/me/roadmaps").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].roadmapId").value(roadmapId))
                .andExpect(jsonPath("$[0].completedNodes").value(0))
                .andExpect(jsonPath("$[0].totalNodes").value(5));

        // Complete the first node -> 20%
        mockMvc.perform(put("/api/me/roadmaps/{r}/nodes/{n}/progress", roadmapId, firstNodeId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.percent").value(20));

        // Completing twice is idempotent
        mockMvc.perform(put("/api/me/roadmaps/{r}/nodes/{n}/progress", roadmapId, firstNodeId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCount").value(1));

        // Detail view carries the progress overlay
        mockMvc.perform(get("/api/me/roadmaps/{r}", roadmapId).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roadmap.id").value(roadmapId))
                .andExpect(jsonPath("$.progress.completedNodeIds[0]").value(firstNodeId))
                .andExpect(jsonPath("$.progress.percent").value(20));

        // A second user saves the SAME shared roadmap; their progress is independent
        String bobToken = registerAndLogin("second@example.com");
        mockMvc.perform(post("/api/me/roadmaps")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareToken\":\"" + shareToken + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roadmap.id").value(roadmapId))
                .andExpect(jsonPath("$.progress.completedCount").value(0));

        // Un-complete -> back to 0%
        mockMvc.perform(put("/api/me/roadmaps/{r}/nodes/{n}/progress", roadmapId, firstNodeId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percent").value(0));

        // Progress on a node that doesn't belong to the roadmap -> 404
        mockMvc.perform(put("/api/me/roadmaps/{r}/nodes/{n}/progress", roadmapId, 999999)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isNotFound());

        // Remove from library; the shared roadmap itself survives for other users
        mockMvc.perform(delete("/api/me/roadmaps/{r}", roadmapId).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/roadmaps").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/me/roadmaps/{r}", roadmapId).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/roadmaps/public/{t}", shareToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me/roadmaps").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
    }
}
