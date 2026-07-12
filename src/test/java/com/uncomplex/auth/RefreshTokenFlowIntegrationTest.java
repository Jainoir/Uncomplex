package com.uncomplex.auth;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void refreshRotatesAndReuseKillsAllSessions() throws Exception {
        JsonNode registered = postJson("/api/auth/register",
                "{\"email\":\"rotate@example.com\",\"password\":\"a-strong-password\"}", 201);
        String firstRefresh = registered.get("refreshToken").asText();
        assertThat(firstRefresh).isNotBlank();
        assertThat(registered.get("refreshExpiresAt").asText()).isNotBlank();

        // Rotate: new pair comes back, and the new access token works
        JsonNode rotated = postJson("/api/auth/refresh",
                "{\"refreshToken\":\"" + firstRefresh + "\"}", 200);
        String secondRefresh = rotated.get("refreshToken").asText();
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);
        mockMvc.perform(get("/api/me/roadmaps")
                        .header("Authorization", "Bearer " + rotated.get("token").asText()))
                .andExpect(status().isOk());

        // Replaying the rotated (revoked) token = theft signal -> 401 AND every
        // outstanding session for the user is revoked, including the newest one.
        postJson("/api/auth/refresh", "{\"refreshToken\":\"" + firstRefresh + "\"}", 401);
        postJson("/api/auth/refresh", "{\"refreshToken\":\"" + secondRefresh + "\"}", 401);
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        JsonNode registered = postJson("/api/auth/register",
                "{\"email\":\"logout@example.com\",\"password\":\"a-strong-password\"}", 201);
        String refresh = registered.get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());

        postJson("/api/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}", 401);
    }

    @Test
    void garbageRefreshTokenIsRejected() throws Exception {
        postJson("/api/auth/refresh", "{\"refreshToken\":\"not-a-real-token\"}", 401);
    }

    private JsonNode postJson(String path, String body, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(content);
    }
}
