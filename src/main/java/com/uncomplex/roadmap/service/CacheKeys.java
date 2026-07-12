package com.uncomplex.roadmap.service;

import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;

import java.util.Locale;

/**
 * Builds the normalized cache key so that "Rate Limiting", "rate  limiting" and
 * "RATE-LIMITING" for the same level and goal all resolve to one stored roadmap
 * (one AI call, ever).
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    public static String of(String topic, ExperienceLevel level, LearningGoal goal) {
        return normalizeTopic(topic) + "|" + level.name().toLowerCase(Locale.ROOT)
                + "|" + goal.name().toLowerCase(Locale.ROOT);
    }

    public static String normalizeTopic(String topic) {
        return topic.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]+", "-")
                .replaceAll("-{2,}", "-");
    }
}
