package com.uncomplex.roadmap.dto;

import com.uncomplex.roadmap.model.Difficulty;
import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;
import com.uncomplex.roadmap.model.SourceType;

import java.time.Instant;
import java.util.List;

public record RoadmapResponse(
        Long id,
        String shareToken,
        String shareUrl,
        String topic,
        String title,
        String summary,
        ExperienceLevel experienceLevel,
        LearningGoal goal,
        int estimatedTotalMinutes,
        Instant createdAt,
        List<NodeResponse> prerequisites
) {

    public record NodeResponse(
            Long id,
            String name,
            String description,
            String reason,
            Difficulty difficulty,
            int estimatedMinutes,
            int position,
            List<ResourceResponse> resources
    ) {
    }

    public record ResourceResponse(
            String title,
            String url,
            SourceType sourceType,
            String credibilityReason
    ) {
    }
}
