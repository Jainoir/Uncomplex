package com.uncomplex.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.AnthropicIoException;
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

            Many topics mean different things in different fields — "integration" is CI/CD to
            one reader and REST APIs to another. Resolve the ambiguity explicitly:

            - If a field is given, interpret the topic strictly within that field.
            - If none is given, choose the most common software-development reading.
            - Either way, the title MUST name the interpretation you chose, not just echo the
              topic. Write "Continuous Integration (automated build and test pipelines)",
              never a bare "Integration". A reader who meant something else must be able to
              tell at a glance that they did.
            - Say in the summary which reading you took and, when the topic was ambiguous,
              what the other common readings are.

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
    public RoadmapDraft generate(String topic, String context, ExperienceLevel level, LearningGoal goal) {
        StructuredMessageCreateParams<RoadmapDraft> params = MessageCreateParams.builder()
                .model(settings.model())
                .maxTokens(settings.maxOutputTokens())
                .thinking(ThinkingConfigAdaptive.builder().build())
                .system(SYSTEM_PROMPT)
                .outputConfig(RoadmapDraft.class)
                .addUserMessage(userPrompt(topic, context, level, goal))
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
        } catch (AnthropicIoException e) {
            throw new AiGenerationException("Anthropic API request timed out or could not connect", e);
        }
    }

    private String userPrompt(String topic, String context, ExperienceLevel level, LearningGoal goal) {
        String field = (context == null || context.isBlank())
                ? "Field: not specified — infer the most common software meaning of the topic."
                : "Field: %s — interpret the topic within this field.".formatted(context.trim());
        return """
                Topic: %s
                %s
                Learner experience level: %s
                Learning goal: %s

                Build the prerequisite roadmap for this learner.
                """.formatted(topic, field, level, goal);
    }
}
