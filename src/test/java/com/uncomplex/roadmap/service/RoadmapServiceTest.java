package com.uncomplex.roadmap.service;

import com.uncomplex.TestFixtures;
import com.uncomplex.ai.AiRoadmapGenerator;
import com.uncomplex.ai.InvalidDraftException;
import com.uncomplex.ai.RoadmapDraftValidator;
import com.uncomplex.resource.ResourceCredibilityService;
import com.uncomplex.roadmap.entity.Roadmap;
import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;
import com.uncomplex.roadmap.repository.RoadmapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoadmapServiceTest {

    @Mock
    private RoadmapRepository repository;

    @Mock
    private AiRoadmapGenerator generator;

    private RoadmapService service;

    @BeforeEach
    void setUp() {
        var properties = TestFixtures.appProperties();
        var validator = new RoadmapDraftValidator(properties, new ResourceCredibilityService(properties));
        service = new RoadmapService(repository, generator, validator, properties);
    }

    @Test
    void cachedRoadmapIsServedWithoutCallingTheAiProvider() {
        Roadmap cached = new Roadmap("rate-limiting|beginner|system_design_interview", "rate-limiting-abcde",
                "Rate limiting", "Learn Rate Limiting", "Summary",
                ExperienceLevel.BEGINNER, LearningGoal.SYSTEM_DESIGN_INTERVIEW);
        when(repository.findByCacheKey("rate-limiting|beginner|system_design_interview"))
                .thenReturn(Optional.of(cached));

        Roadmap result = service.getOrGenerate("Rate Limiting",
                ExperienceLevel.BEGINNER, LearningGoal.SYSTEM_DESIGN_INTERVIEW);

        assertThat(result).isSameAs(cached);
        verify(generator, never()).generate(anyString(), any(), any());
    }

    @Test
    void invalidModelOutputIsRetriedOnce() {
        when(repository.findByCacheKey(anyString())).thenReturn(Optional.empty());
        when(repository.existsByShareToken(anyString())).thenReturn(false);
        when(repository.saveAndFlush(any(Roadmap.class))).thenAnswer(inv -> inv.getArgument(0));
        when(generator.generate(anyString(), any(), any()))
                .thenReturn(TestFixtures.draftWithPrerequisites(2))   // invalid: too few
                .thenReturn(TestFixtures.draftWithPrerequisites(5));  // valid on retry

        Roadmap result = service.getOrGenerate("Docker", ExperienceLevel.BEGINNER, LearningGoal.BUILD_A_PROJECT);

        assertThat(result.getNodes()).hasSize(5);
        verify(generator, times(2)).generate(anyString(), any(), any());
    }

    @Test
    void repeatedlyInvalidOutputFailsAfterMaxAttempts() {
        when(repository.findByCacheKey(anyString())).thenReturn(Optional.empty());
        when(generator.generate(anyString(), any(), any()))
                .thenReturn(TestFixtures.draftWithPrerequisites(1));

        assertThatThrownBy(() ->
                service.getOrGenerate("Docker", ExperienceLevel.BEGINNER, LearningGoal.BUILD_A_PROJECT))
                .isInstanceOf(InvalidDraftException.class);
        verify(generator, times(2)).generate(anyString(), any(), any());
    }

    @Test
    void totalMinutesIsTheSumOfNodeEstimates() {
        when(repository.findByCacheKey(anyString())).thenReturn(Optional.empty());
        when(repository.existsByShareToken(anyString())).thenReturn(false);
        when(repository.saveAndFlush(any(Roadmap.class))).thenAnswer(inv -> inv.getArgument(0));
        when(generator.generate(anyString(), any(), any()))
                .thenReturn(TestFixtures.draftWithPrerequisites(4)); // 4 x 30 minutes

        Roadmap result = service.getOrGenerate("Docker", ExperienceLevel.BEGINNER, LearningGoal.BUILD_A_PROJECT);

        assertThat(result.getEstimatedTotalMinutes()).isEqualTo(120);
    }
}
