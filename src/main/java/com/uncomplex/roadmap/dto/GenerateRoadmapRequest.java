package com.uncomplex.roadmap.dto;

import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GenerateRoadmapRequest(
        @NotBlank(message = "topic must not be blank")
        @Size(max = 120, message = "topic must be at most 120 characters")
        @Pattern(regexp = "[\\p{L}\\p{N} .,+#/&()'-]+", message = "topic contains unsupported characters")
        String topic,

        @NotNull(message = "experienceLevel is required")
        ExperienceLevel experienceLevel,

        @NotNull(message = "goal is required")
        LearningGoal goal
) {
}
