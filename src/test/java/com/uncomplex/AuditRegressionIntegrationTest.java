package com.uncomplex;

import com.uncomplex.ai.AiRoadmapGenerator;
import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;
import com.uncomplex.roadmap.service.CacheKeys;
import com.uncomplex.roadmap.service.RoadmapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inputs the API advertised as valid but could not actually process, each of which
 * surfaced as a 500.
 */
@SpringBootTest(properties = "app.link-health.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditRegressionIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired RoadmapService roadmaps;
    @MockitoBean AiRoadmapGenerator generator;

    @BeforeEach
    void setUp() {
        when(generator.generate(anyString(), any(), any(), any()))
                .thenReturn(TestFixtures.draftWithPrerequisites(4));
    }

    @Test
    void theLongestValidTopicAndContextStillFitTheCacheColumn() {
        String topic = "a".repeat(120);
        String context = "b".repeat(120);

        // 120 + 1 + 120 + 1 + "beginner" + 1 + "build_a_project" = 266, past the old 255.
        assertThat(CacheKeys.of(topic, context, ExperienceLevel.BEGINNER, LearningGoal.BUILD_A_PROJECT))
                .hasSizeGreaterThan(255);

        var roadmap = roadmaps.getOrGenerate(topic, context, ExperienceLevel.BEGINNER,
                LearningGoal.BUILD_A_PROJECT);

        assertThat(roadmap.getId()).isNotNull();
        // And it is reachable again, so the row really persisted at full width.
        assertThat(roadmaps.getOrGenerate(topic, context, ExperienceLevel.BEGINNER,
                LearningGoal.BUILD_A_PROJECT).getId()).isEqualTo(roadmap.getId());
    }

    @Test
    void maximumLengthRequestIsAcceptedOverHttp() throws Exception {
        String body = """
                {"topic":"%s","context":"%s","experienceLevel":"BEGINNER","goal":"BUILD_A_PROJECT"}
                """.formatted("c".repeat(120), "d".repeat(120));

        mvc.perform(post("/api/roadmaps").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void aPasswordBeyondTheEncoderLimitIsRejectedAsValidationNotAsServerError() throws Exception {
        String body = """
                {"email":"long-password@example.com","password":"%s"}
                """.formatted("x".repeat(73));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void multibytePasswordsAreMeasuredInBytesNotCharacters() throws Exception {
        // 40 characters, 80 bytes: within any character limit, past BCrypt's byte limit.
        String body = """
                {"email":"accented-password@example.com","password":"%s"}
                """.formatted("é".repeat(40));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPasswordAtTheLimitIsStillAccepted() throws Exception {
        String body = """
                {"email":"exactly-72@example.com","password":"%s"}
                """.formatted("y".repeat(72));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
