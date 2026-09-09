package com.uncomplex.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.cors.allowed-origins=https://uncomplex.vercel.app")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthCorsIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void theFrontendCanWakeTheApiWithoutCredentials() throws Exception {
        mvc.perform(get("/actuator/health").header("Origin", "https://uncomplex.vercel.app"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://uncomplex.vercel.app"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void anUnlistedOriginIsStillRejected() throws Exception {
        mvc.perform(get("/actuator/health").header("Origin", "https://unlisted.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
