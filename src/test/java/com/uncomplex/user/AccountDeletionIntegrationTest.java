package com.uncomplex.user;

import com.uncomplex.TestFixtures;
import com.uncomplex.ai.AiRoadmapGenerator;
import com.uncomplex.auth.AuthService;
import com.uncomplex.auth.RefreshTokenRepository;
import com.uncomplex.exception.InvalidCredentialsException;
import com.uncomplex.library.repository.NodeProgressRepository;
import com.uncomplex.library.repository.SavedRoadmapRepository;
import com.uncomplex.library.service.LibraryService;
import com.uncomplex.roadmap.entity.Roadmap;
import com.uncomplex.roadmap.repository.RoadmapRepository;
import com.uncomplex.roadmap.service.RoadmapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static com.uncomplex.roadmap.model.ExperienceLevel.BEGINNER;
import static com.uncomplex.roadmap.model.LearningGoal.BUILD_A_PROJECT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "app.link-health.enabled=false")
@ActiveProfiles("test")
class AccountDeletionIntegrationTest {

    @Autowired AccountService accounts;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired LibraryService library;
    @Autowired RoadmapService roadmaps;
    @Autowired SavedRoadmapRepository savedRoadmaps;
    @Autowired NodeProgressRepository nodeProgress;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired RoadmapRepository roadmapRepository;
    @MockitoBean AiRoadmapGenerator generator;

    @BeforeEach
    void setUp() {
        when(generator.generate(anyString(), any(), any()))
                .thenReturn(TestFixtures.draftWithPrerequisites(4));
    }

    @Test
    void deletingAnAccountRemovesEverythingReferencingItButKeepsSharedRoadmaps() {
        String email = UUID.randomUUID() + "@example.com";
        var session = auth.register(email, "a-strong-password");
        Long userId = users.findByEmail(email).orElseThrow().getId();

        Roadmap roadmap = roadmaps.getOrGenerate("Erasure " + UUID.randomUUID(), BEGINNER, BUILD_A_PROJECT);
        library.saveByShareToken(userId, roadmap.getShareToken());
        Long nodeId = roadmap.getNodes().getFirst().getId();
        library.setNodeProgress(userId, roadmap.getId(), nodeId, true);

        assertThat(savedRoadmaps.findAllByUserIdWithRoadmap(userId)).isNotEmpty();
        assertThat(nodeProgress.findCompletedNodeIds(userId, roadmap.getId())).isNotEmpty();

        accounts.deleteAccount(userId);

        assertThat(users.findById(userId)).isEmpty();
        assertThat(savedRoadmaps.findAllByUserIdWithRoadmap(userId)).isEmpty();
        assertThat(nodeProgress.findCompletedNodeIds(userId, roadmap.getId())).isEmpty();
        assertThat(refreshTokens.findAll().stream().anyMatch(t -> t.getUser().getId().equals(userId))).isFalse();

        // Shared content survives: other people's libraries and share links depend on it.
        assertThat(roadmapRepository.findByShareToken(roadmap.getShareToken())).isPresent();

        // The presented refresh token dies with the account.
        assertThatThrownBy(() -> auth.refresh(session.refreshToken()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void theSameEmailCanRegisterAgainAfterDeletion() {
        String email = UUID.randomUUID() + "@example.com";
        auth.register(email, "a-strong-password");
        accounts.deleteAccount(users.findByEmail(email).orElseThrow().getId());

        assertThat(users.existsByEmail(email)).isFalse();
        auth.register(email, "a-different-password");
        assertThat(users.existsByEmail(email)).isTrue();
    }
}
