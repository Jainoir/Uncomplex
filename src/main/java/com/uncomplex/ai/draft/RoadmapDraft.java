package com.uncomplex.ai.draft;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Raw AI output for a roadmap. This is the JSON schema the model is constrained to
 * (via structured outputs) — it is treated as untrusted input and passed through
 * {@link com.uncomplex.ai.RoadmapDraftValidator} before anything is persisted.
 */
public record RoadmapDraft(
        @JsonPropertyDescription("Short roadmap title, e.g. 'Learn Rate Limiting'")
        String title,

        @JsonPropertyDescription("One or two sentences describing what this roadmap prepares the learner for")
        String summary,

        @JsonPropertyDescription("Ordered prerequisite concepts, most foundational first. Between 4 and 8 entries.")
        List<PrerequisiteDraft> prerequisites
) {
}
