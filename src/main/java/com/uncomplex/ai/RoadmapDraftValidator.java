package com.uncomplex.ai;

import com.uncomplex.ai.draft.PrerequisiteDraft;
import com.uncomplex.ai.draft.ResourceDraft;
import com.uncomplex.config.AppProperties;
import com.uncomplex.resource.ResourceCredibilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Sanitizes AI output before it is persisted. Structured outputs guarantee the JSON
 * matches the schema, but not that the content respects our product rules — so this
 * class enforces them: prerequisite count bounds, sane time estimates, and a
 * credibility allowlist for every resource URL.
 */
@Component
public class RoadmapDraftValidator {

    private static final Logger log = LoggerFactory.getLogger(RoadmapDraftValidator.class);

    private static final int MIN_MINUTES = 5;
    private static final int MAX_MINUTES = 480;

    private final AppProperties.Generation generation;
    private final ResourceCredibilityService credibilityService;

    public RoadmapDraftValidator(AppProperties properties, ResourceCredibilityService credibilityService) {
        this.generation = properties.generation();
        this.credibilityService = credibilityService;
    }

    /**
     * Returns a sanitized copy of the draft, or throws {@link InvalidDraftException}
     * when the draft is structurally unusable (wrong prerequisite count, blank fields).
     */
    public SanitizedDraft validate(com.uncomplex.ai.draft.RoadmapDraft draft) {
        if (draft == null || isBlank(draft.title()) || isBlank(draft.summary())) {
            throw new InvalidDraftException("Draft is missing a title or summary");
        }
        List<PrerequisiteDraft> prerequisites = draft.prerequisites();
        if (prerequisites == null
                || prerequisites.size() < generation.minPrerequisites()
                || prerequisites.size() > generation.maxPrerequisites()) {
            int size = prerequisites == null ? 0 : prerequisites.size();
            throw new InvalidDraftException("Expected between " + generation.minPrerequisites() + " and "
                    + generation.maxPrerequisites() + " prerequisites but got " + size);
        }

        List<SanitizedPrerequisite> sanitized = prerequisites.stream()
                .sorted(Comparator.comparingInt(PrerequisiteDraft::position))
                .map(this::sanitizePrerequisite)
                .toList();

        return new SanitizedDraft(draft.title().trim(), draft.summary().trim(), sanitized);
    }

    private SanitizedPrerequisite sanitizePrerequisite(PrerequisiteDraft p) {
        if (isBlank(p.name()) || isBlank(p.description()) || isBlank(p.reason()) || p.difficulty() == null) {
            throw new InvalidDraftException("Prerequisite has blank required fields");
        }
        int minutes = Math.clamp(p.estimatedMinutes(), MIN_MINUTES, MAX_MINUTES);

        List<ResourceDraft> resources = p.resources() == null ? List.of() : p.resources().stream()
                .filter(this::isUsableResource)
                .limit(generation.maxResourcesPerPrerequisite())
                .toList();

        return new SanitizedPrerequisite(
                p.name().trim(), p.description().trim(), p.reason().trim(),
                p.difficulty(), minutes, resources);
    }

    private boolean isUsableResource(ResourceDraft r) {
        if (r == null || isBlank(r.title()) || r.sourceType() == null || isBlank(r.credibilityReason())) {
            return false;
        }
        boolean allowed = credibilityService.isAllowed(r.url());
        if (!allowed) {
            log.info("Dropping resource with non-allowlisted URL: {}", r.url());
        }
        return allowed;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Draft after validation: ordered, clamped, resources filtered to the allowlist. */
    public record SanitizedDraft(String title, String summary, List<SanitizedPrerequisite> prerequisites) {
    }

    public record SanitizedPrerequisite(
            String name,
            String description,
            String reason,
            com.uncomplex.roadmap.model.Difficulty difficulty,
            int estimatedMinutes,
            List<ResourceDraft> resources
    ) {
    }
}
