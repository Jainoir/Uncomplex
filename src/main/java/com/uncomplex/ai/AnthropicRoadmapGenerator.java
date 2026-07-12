package com.uncomplex.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.uncomplex.ai.draft.RoadmapDraft;
import com.uncomplex.config.AppProperties;
import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates roadmaps with the Anthropic Messages API. Structured outputs constrain
 * the response to the {@link RoadmapDraft} JSON schema, so no hand-rolled JSON
 * parsing or "please answer in JSON" prompting is needed.
 */
public class AnthropicRoadmapGenerator implements AiRoadmapGenerator {

    private static final Logger log = LoggerFactory.getLogger(AnthropicRoadmapGenerator.class);

    private static final String SYSTEM_PROMPT = """
            You design prerequisite learning roadmaps for software development topics.
            Given a topic, the learner's experience level, and their goal, list the concepts \
            they should understand BEFORE studying the topic itself, in learning order.

            Rules:
            - Between 4 and 8 prerequisite concepts, most foundational first.
            - Only directly relevant prerequisites. Do not recurse into distant fundamentals \
            (Kubernetes must not lead back to electronics or CPU architecture).
            - Skip concepts the learner already knows at their stated experience level.
            - Prefer practical foundations over theory.
            - Keep explanations short and beginner-friendly.
            - Estimated minutes must be realistic for learning the essentials, not mastery.
            - Resources: at most two per concept, and only real, well-known pages from official \
            documentation, standards bodies, universities, or recognized educational sites \
            (developer.mozilla.org, docs.oracle.com, spring.io, learn.microsoft.com, kubernetes.io, \
            docs.docker.com, postgresql.org, redis.io, github.com, aws.amazon.com, cloud.google.com, \
            web.dev, datatracker.ietf.org, owasp.org, martinfowler.com, microservices.io, *.edu). \
            If you are not confident a URL exists, omit the resource entirely.
            """;

    private final AnthropicClient client;
    private final AppProperties.Ai.Anthropic settings;

    public AnthropicRoadmapGenerator(AnthropicClient client, AppProperties properties) {
        this.client = client;
        this.settings = properties.ai().anthropic();
    }

    @Override
    public RoadmapDraft generate(String topic, ExperienceLevel level, LearningGoal goal) {
        StructuredMessageCreateParams<RoadmapDraft> params = MessageCreateParams.builder()
                .model(settings.model())
                .maxTokens(settings.maxOutputTokens())
                .thinking(ThinkingConfigAdaptive.builder().build())
                .system(SYSTEM_PROMPT)
                .outputConfig(RoadmapDraft.class)
                .addUserMessage(userPrompt(topic, level, goal))
                .build();

        try {
            return client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(typed -> typed.text())
                    .orElseThrow(() -> new AiGenerationException(
                            "Model response contained no structured roadmap (possible refusal)"));
        } catch (AnthropicServiceException e) {
            log.error("Anthropic API call failed with status {}", e.statusCode(), e);
            throw new AiGenerationException("Anthropic API call failed", e);
        }
    }

    private String userPrompt(String topic, ExperienceLevel level, LearningGoal goal) {
        return """
                Topic: %s
                Learner experience level: %s
                Learning goal: %s

                Build the prerequisite roadmap for this learner.
                """.formatted(topic, level, goal);
    }
}
