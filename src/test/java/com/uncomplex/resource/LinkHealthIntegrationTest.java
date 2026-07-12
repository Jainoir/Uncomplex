package com.uncomplex.resource;

import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;
import com.uncomplex.roadmap.service.RoadmapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The network is faked via UrlProber; everything else (persistence, the check run,
 * the reachability flag surfacing in the public API) is real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LinkHealthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoadmapService roadmapService;

    @Autowired
    private LinkHealthService linkHealthService;

    @MockitoBean
    private UrlProber urlProber;

    @Test
    void deadLinksAreRecordedAndSurfacedInTheApi() throws Exception {
        // Mock generator produces two resources: a Client-Server page and an HTTP overview page
        String shareToken = roadmapService
                .getOrGenerate("Link health", ExperienceLevel.BEGINNER, LearningGoal.GENERAL_UNDERSTANDING)
                .getShareToken();

        when(urlProber.isReachable(anyString())).thenReturn(true);
        when(urlProber.isReachable(contains("Client-Server"))).thenReturn(false);

        LinkHealthService.CheckResult result = linkHealthService.checkAll();

        // The H2 database is shared with other test classes in the same context, so
        // assert relatively: at least this roadmap's two resources were checked.
        assertThat(result.checked()).isGreaterThanOrEqualTo(2);
        assertThat(result.unreachable()).isGreaterThanOrEqualTo(1);
        assertThat(result.unreachable()).isLessThan(result.checked());

        mockMvc.perform(get("/api/roadmaps/public/{token}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prerequisites[0].resources[0].reachable").value(false))
                .andExpect(jsonPath("$.prerequisites[1].resources[0].reachable").value(true));
    }
}
