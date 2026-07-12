package com.uncomplex.roadmap.service;

import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeysTest {

    @Test
    void sameTopicWithDifferentCasingAndSpacingProducesSameKey() {
        String a = CacheKeys.of("Rate Limiting", ExperienceLevel.BEGINNER, LearningGoal.SYSTEM_DESIGN_INTERVIEW);
        String b = CacheKeys.of("  rate   limiting ", ExperienceLevel.BEGINNER, LearningGoal.SYSTEM_DESIGN_INTERVIEW);
        String c = CacheKeys.of("RATE_LIMITING", ExperienceLevel.BEGINNER, LearningGoal.SYSTEM_DESIGN_INTERVIEW);

        assertThat(a).isEqualTo(b).isEqualTo(c)
                .isEqualTo("rate-limiting|beginner|system_design_interview");
    }

    @Test
    void differentLevelOrGoalProducesDifferentKey() {
        String beginner = CacheKeys.of("Docker", ExperienceLevel.BEGINNER, LearningGoal.BUILD_A_PROJECT);
        String advanced = CacheKeys.of("Docker", ExperienceLevel.ADVANCED, LearningGoal.BUILD_A_PROJECT);
        String interview = CacheKeys.of("Docker", ExperienceLevel.BEGINNER, LearningGoal.JOB_INTERVIEW);

        assertThat(beginner).isNotEqualTo(advanced).isNotEqualTo(interview);
    }
}
