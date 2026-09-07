package com.uncomplex.roadmap.service;

import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeysTest {

    @Test
    void sameTopicWithDifferentCasingAndSpacingProducesSameKey() {
        String a = CacheKeys.of("Rate Limiting", null, ExperienceLevel.BEGINNER, LearningGoal.SYSTEM_DESIGN_INTERVIEW);
        String b = CacheKeys.of("  rate   limiting ", null, ExperienceLevel.BEGINNER, LearningGoal.SYSTEM_DESIGN_INTERVIEW);
        String c = CacheKeys.of("RATE_LIMITING", null, ExperienceLevel.BEGINNER, LearningGoal.SYSTEM_DESIGN_INTERVIEW);

        assertThat(a).isEqualTo(b).isEqualTo(c)
                .isEqualTo("rate-limiting|beginner|system_design_interview");
    }

    @Test
    void differentLevelOrGoalProducesDifferentKey() {
        String beginner = CacheKeys.of("Docker", null, ExperienceLevel.BEGINNER, LearningGoal.BUILD_A_PROJECT);
        String advanced = CacheKeys.of("Docker", null, ExperienceLevel.ADVANCED, LearningGoal.BUILD_A_PROJECT);
        String interview = CacheKeys.of("Docker", null, ExperienceLevel.BEGINNER, LearningGoal.JOB_INTERVIEW);

        assertThat(beginner).isNotEqualTo(advanced).isNotEqualTo(interview);
    }

    @Test
    void aBlankContextKeepsTheOriginalKeyExactly() {
        // Load-bearing: roadmaps generated before context existed must stay reachable.
        // Changing the shape of the no-context key would silently orphan every stored
        // roadmap and pay to generate all of them again.
        String expected = "integration|beginner|general_understanding";
        assertThat(CacheKeys.of("Integration", null, ExperienceLevel.BEGINNER,
                LearningGoal.GENERAL_UNDERSTANDING)).isEqualTo(expected);
        assertThat(CacheKeys.of("Integration", "", ExperienceLevel.BEGINNER,
                LearningGoal.GENERAL_UNDERSTANDING)).isEqualTo(expected);
        assertThat(CacheKeys.of("Integration", "   ", ExperienceLevel.BEGINNER,
                LearningGoal.GENERAL_UNDERSTANDING)).isEqualTo(expected);
    }

    @Test
    void theSameTopicInDifferentFieldsResolvesToDifferentRoadmaps() {
        String ci = CacheKeys.of("Integration", "CI/CD pipelines", ExperienceLevel.BEGINNER,
                LearningGoal.GENERAL_UNDERSTANDING);
        String apis = CacheKeys.of("Integration", "REST APIs", ExperienceLevel.BEGINNER,
                LearningGoal.GENERAL_UNDERSTANDING);
        String none = CacheKeys.of("Integration", null, ExperienceLevel.BEGINNER,
                LearningGoal.GENERAL_UNDERSTANDING);

        assertThat(ci).isNotEqualTo(apis).isNotEqualTo(none);
        assertThat(apis).isNotEqualTo(none);
    }

    @Test
    void contextIsNormalizedTheSameWayAsTheTopic() {
        String a = CacheKeys.of("Integration", "CI/CD  Pipelines", ExperienceLevel.BEGINNER,
                LearningGoal.GENERAL_UNDERSTANDING);
        String b = CacheKeys.of("integration", "  ci/cd_pipelines ", ExperienceLevel.BEGINNER,
                LearningGoal.GENERAL_UNDERSTANDING);

        assertThat(a).isEqualTo(b);
    }
}
