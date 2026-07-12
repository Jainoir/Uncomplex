package com.uncomplex;

import com.uncomplex.ai.draft.PrerequisiteDraft;
import com.uncomplex.ai.draft.ResourceDraft;
import com.uncomplex.ai.draft.RoadmapDraft;
import com.uncomplex.config.AppProperties;
import com.uncomplex.roadmap.model.Difficulty;
import com.uncomplex.roadmap.model.SourceType;

import java.util.ArrayList;
import java.util.List;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static AppProperties appProperties() {
        return new AppProperties(
                new AppProperties.Ai("mock", new AppProperties.Ai.Anthropic("", "claude-opus-4-8", 16000L)),
                new AppProperties.Generation(4, 8, 2, 2),
                new AppProperties.RateLimit(10),
                new AppProperties.Credibility(List.of("developer.mozilla.org", "spring.io", "docs.spring.io")));
    }

    public static RoadmapDraft draftWithPrerequisites(int count) {
        List<PrerequisiteDraft> prerequisites = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            prerequisites.add(prerequisite("Concept " + i, i, List.of(allowedResource())));
        }
        return new RoadmapDraft("Learn Testing", "A summary.", prerequisites);
    }

    public static PrerequisiteDraft prerequisite(String name, int position, List<ResourceDraft> resources) {
        return new PrerequisiteDraft(name, "What it is.", "Why it matters.",
                Difficulty.BEGINNER, 30, position, resources);
    }

    public static ResourceDraft allowedResource() {
        return new ResourceDraft("MDN HTTP Overview",
                "https://developer.mozilla.org/en-US/docs/Web/HTTP/Overview",
                SourceType.OFFICIAL_DOCUMENTATION, "Official web documentation");
    }

    public static ResourceDraft disallowedResource() {
        return new ResourceDraft("Random blog post",
                "https://some-random-blog.example.com/rate-limiting",
                SourceType.RECOGNIZED_CREATOR, "Popular blog");
    }
}
