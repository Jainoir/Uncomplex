package com.uncomplex.ai.draft;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.uncomplex.roadmap.model.SourceType;

public record ResourceDraft(
        @JsonPropertyDescription("Resource title as it appears on the page")
        String title,

        @JsonPropertyDescription("Full https URL of a real, well-known page. Prefer the stable entry page of official documentation over deep links.")
        String url,

        SourceType sourceType,

        @JsonPropertyDescription("One short line on why this source is credible")
        String credibilityReason
) {
}
