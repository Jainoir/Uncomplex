package com.uncomplex.ai.draft;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.uncomplex.roadmap.model.Difficulty;

import java.util.List;

public record PrerequisiteDraft(
        @JsonPropertyDescription("Concept name, e.g. 'HTTP Requests'")
        String name,

        @JsonPropertyDescription("Two or three beginner-friendly sentences explaining the concept")
        String description,

        @JsonPropertyDescription("Why this concept is needed before the main topic")
        String reason,

        Difficulty difficulty,

        @JsonPropertyDescription("Realistic time to learn the essentials, in minutes (between 15 and 480)")
        int estimatedMinutes,

        @JsonPropertyDescription("1-based position in the recommended learning order")
        int position,

        @JsonPropertyDescription("One or two credible learning resources for this concept")
        List<ResourceDraft> resources
) {
}
