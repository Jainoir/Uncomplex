package com.uncomplex.ai;

import com.uncomplex.ai.draft.PrerequisiteDraft;
import com.uncomplex.ai.draft.ResourceDraft;
import com.uncomplex.ai.draft.RoadmapDraft;
import com.uncomplex.roadmap.model.Difficulty;
import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;
import com.uncomplex.roadmap.model.SourceType;

import java.util.List;

/**
 * Deterministic generator used when no Anthropic API key is configured — keeps the
 * whole application runnable and testable offline.
 */
public class MockRoadmapGenerator implements AiRoadmapGenerator {

    @Override
    public RoadmapDraft generate(String topic, ExperienceLevel level, LearningGoal goal) {
        ResourceDraft mdnHttp = new ResourceDraft(
                "MDN: An overview of HTTP",
                "https://developer.mozilla.org/en-US/docs/Web/HTTP/Overview",
                SourceType.OFFICIAL_DOCUMENTATION,
                "Maintained by Mozilla, the reference documentation for web technologies");
        ResourceDraft mdnClientServer = new ResourceDraft(
                "MDN: Client-Server overview",
                "https://developer.mozilla.org/en-US/docs/Learn/Server-side/First_steps/Client-Server_overview",
                SourceType.OFFICIAL_DOCUMENTATION,
                "Maintained by Mozilla, the reference documentation for web technologies");

        List<PrerequisiteDraft> prerequisites = List.of(
                new PrerequisiteDraft("Client-server architecture",
                        "How clients send requests to servers and receive responses over a network.",
                        "Almost every concept behind '" + topic + "' assumes you know who talks to whom.",
                        Difficulty.BEGINNER, 30, 1, List.of(mdnClientServer)),
                new PrerequisiteDraft("HTTP requests and responses",
                        "The protocol used for most client-server communication: methods, status codes, headers.",
                        "You need to recognize requests and responses to reason about " + topic + ".",
                        Difficulty.BEGINNER, 45, 2, List.of(mdnHttp)),
                new PrerequisiteDraft("REST APIs",
                        "A convention for structuring HTTP endpoints around resources.",
                        "Most systems where " + topic + " matters expose REST APIs.",
                        Difficulty.BEGINNER, 60, 3, List.of()),
                new PrerequisiteDraft("Caching basics",
                        "Storing computed results so repeated requests are served cheaply.",
                        "Caching shapes the performance characteristics that make " + topic + " relevant.",
                        Difficulty.INTERMEDIATE, 45, 4, List.of()),
                new PrerequisiteDraft("Distributed systems basics",
                        "What changes when a system runs on multiple machines: state, coordination, failure.",
                        "At scale, " + topic + " is a distributed systems problem.",
                        Difficulty.INTERMEDIATE, 90, 5, List.of())
        );

        return new RoadmapDraft(
                "Learn " + topic,
                "The foundational concepts a " + level.name().toLowerCase() + " learner should understand before "
                        + topic + " (goal: " + goal.name().toLowerCase().replace('_', ' ') + "). [mock data]",
                prerequisites);
    }
}
