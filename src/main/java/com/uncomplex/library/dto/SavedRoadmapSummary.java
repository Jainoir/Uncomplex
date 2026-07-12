package com.uncomplex.library.dto;

import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;

import java.time.Instant;

public record SavedRoadmapSummary(
        Long roadmapId,
        String topic,
        String title,
        ExperienceLevel experienceLevel,
        LearningGoal goal,
        int estimatedTotalMinutes,
        String shareToken,
        Instant savedAt,
        long completedNodes,
        int totalNodes
) {
}
