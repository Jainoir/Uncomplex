package com.uncomplex.roadmap.service;

import com.uncomplex.ai.AiRoadmapGenerator;
import com.uncomplex.ai.InvalidDraftException;
import com.uncomplex.ai.RoadmapDraftValidator;
import com.uncomplex.ai.RoadmapDraftValidator.SanitizedDraft;
import com.uncomplex.ai.RoadmapDraftValidator.SanitizedPrerequisite;
import com.uncomplex.ai.draft.ResourceDraft;
import com.uncomplex.ai.draft.RoadmapDraft;
import com.uncomplex.config.AppProperties;
import com.uncomplex.exception.NotFoundException;
import com.uncomplex.roadmap.entity.NodeResource;
import com.uncomplex.roadmap.entity.Roadmap;
import com.uncomplex.roadmap.entity.RoadmapNode;
import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;
import com.uncomplex.roadmap.repository.RoadmapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoadmapService {

    private static final Logger log = LoggerFactory.getLogger(RoadmapService.class);

    private final RoadmapRepository repository;
    private final AiRoadmapGenerator generator;
    private final RoadmapDraftValidator validator;
    private final int maxAttempts;

    public RoadmapService(RoadmapRepository repository, AiRoadmapGenerator generator,
                          RoadmapDraftValidator validator, AppProperties properties) {
        this.repository = repository;
        this.generator = generator;
        this.validator = validator;
        this.maxAttempts = properties.generation().maxAttempts();
    }

    /**
     * Returns the stored roadmap for this (topic, level, goal) combination, generating
     * it with the AI provider only on the first request. Opening a shared link or
     * repeating a request never triggers a new AI call.
     */
    @Transactional
    public Roadmap getOrGenerate(String topic, ExperienceLevel level, LearningGoal goal) {
        String cacheKey = CacheKeys.of(topic, level, goal);
        return initialized(repository.findByCacheKey(cacheKey)
                .orElseGet(() -> generateAndSave(cacheKey, topic, level, goal)));
    }

    @Transactional(readOnly = true)
    public Roadmap getByShareToken(String shareToken) {
        return initialized(repository.findByShareToken(shareToken)
                .orElseThrow(() -> new NotFoundException("No roadmap found for share token " + shareToken)));
    }

    /**
     * Loads the lazy node/resource graph while the transaction is still open
     * (open-in-view is disabled). Bounded work: a roadmap has at most 8 nodes
     * with at most 2 resources each.
     */
    private Roadmap initialized(Roadmap roadmap) {
        roadmap.getNodes().forEach(node -> node.getResources().size());
        return roadmap;
    }

    private Roadmap generateAndSave(String cacheKey, String topic, ExperienceLevel level, LearningGoal goal) {
        SanitizedDraft draft = generateWithRetry(topic, level, goal);
        Roadmap roadmap = toEntity(cacheKey, topic, level, goal, draft);
        try {
            return repository.saveAndFlush(roadmap);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request generated the same roadmap first; serve the winner.
            log.info("Concurrent generation detected for key {}, serving existing roadmap", cacheKey);
            return repository.findByCacheKey(cacheKey).orElseThrow(() -> e);
        }
    }

    private SanitizedDraft generateWithRetry(String topic, ExperienceLevel level, LearningGoal goal) {
        InvalidDraftException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            RoadmapDraft raw = generator.generate(topic, level, goal);
            try {
                return validator.validate(raw);
            } catch (InvalidDraftException e) {
                log.warn("Attempt {}/{}: model output failed validation: {}", attempt, maxAttempts, e.getMessage());
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    private Roadmap toEntity(String cacheKey, String topic, ExperienceLevel level, LearningGoal goal,
                             SanitizedDraft draft) {
        String shareToken = uniqueShareToken(topic);
        Roadmap roadmap = new Roadmap(cacheKey, shareToken, topic.trim(), draft.title(), draft.summary(), level, goal);

        int position = 1;
        for (SanitizedPrerequisite p : draft.prerequisites()) {
            RoadmapNode node = new RoadmapNode(p.name(), p.description(), p.reason(),
                    p.difficulty(), p.estimatedMinutes(), position++);
            for (ResourceDraft r : p.resources()) {
                node.addResource(new NodeResource(r.title(), r.url().trim(), r.sourceType(), r.credibilityReason()));
            }
            roadmap.addNode(node);
        }
        return roadmap;
    }

    private String uniqueShareToken(String topic) {
        for (int i = 0; i < 5; i++) {
            String token = ShareTokens.forTopic(topic);
            if (!repository.existsByShareToken(token)) {
                return token;
            }
        }
        throw new IllegalStateException("Could not generate a unique share token for topic " + topic);
    }
}
