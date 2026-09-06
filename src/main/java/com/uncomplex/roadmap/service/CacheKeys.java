package com.uncomplex.roadmap.service;

import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;

import java.util.Locale;

/**
 * Builds the normalized cache key so that "Rate Limiting", "rate  limiting" and
 * "RATE-LIMITING" for the same level and goal all resolve to one stored roadmap
 * (successful results are reused).
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    /**
     * A blank context deliberately produces the original three-part key. Roadmaps generated
     * before context existed stay reachable, so adding this feature does not silently
     * invalidate the cache and pay for every roadmap to be generated a second time.
     * Neither topic nor context can contain "|" (see GenerateRoadmapRequest), so a context
     * cannot forge a key belonging to a different topic.
     */
    public static String of(String topic, String context, ExperienceLevel level, LearningGoal goal) {
        String suffix = "|" + level.name().toLowerCase(Locale.ROOT)
                + "|" + goal.name().toLowerCase(Locale.ROOT);
        String normalizedContext = context == null ? "" : normalizeTopic(context);
        return normalizedContext.isEmpty()
                ? normalizeTopic(topic) + suffix
                : normalizeTopic(topic) + "|" + normalizedContext + suffix;
    }

    public static String normalizeTopic(String topic) {
        return topic.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]+", "-")
                .replaceAll("-{2,}", "-");
    }
}
