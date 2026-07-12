package com.uncomplex.ai;

import com.uncomplex.ai.draft.RoadmapDraft;
import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;

/**
 * Produces a prerequisite roadmap draft for a topic. Implementations: a real
 * Anthropic-backed generator and a deterministic mock for offline development and tests.
 */
public interface AiRoadmapGenerator {

    RoadmapDraft generate(String topic, ExperienceLevel level, LearningGoal goal);
}
