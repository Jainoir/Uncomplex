package com.uncomplex.library.service;

import com.uncomplex.exception.NotFoundException;
import com.uncomplex.library.dto.RoadmapProgressResponse;
import com.uncomplex.library.dto.SavedRoadmapSummary;
import com.uncomplex.library.entity.NodeProgress;
import com.uncomplex.library.entity.SavedRoadmap;
import com.uncomplex.library.repository.NodeProgressRepository;
import com.uncomplex.library.repository.SavedRoadmapRepository;
import com.uncomplex.roadmap.entity.Roadmap;
import com.uncomplex.roadmap.entity.RoadmapNode;
import com.uncomplex.roadmap.mapper.RoadmapMapper;
import com.uncomplex.roadmap.repository.RoadmapRepository;
import com.uncomplex.user.AppUser;
import com.uncomplex.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LibraryService {

    private final SavedRoadmapRepository savedRoadmaps;
    private final NodeProgressRepository nodeProgress;
    private final RoadmapRepository roadmaps;
    private final UserRepository users;
    private final RoadmapMapper mapper;

    public LibraryService(SavedRoadmapRepository savedRoadmaps, NodeProgressRepository nodeProgress,
                          RoadmapRepository roadmaps, UserRepository users, RoadmapMapper mapper) {
        this.savedRoadmaps = savedRoadmaps;
        this.nodeProgress = nodeProgress;
        this.roadmaps = roadmaps;
        this.users = users;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<SavedRoadmapSummary> listFor(Long userId) {
        return savedRoadmaps.findAllByUserIdWithRoadmap(userId).stream()
                .map(saved -> toSummary(userId, saved))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoadmapProgressResponse getWithProgress(Long userId, Long roadmapId) {
        SavedRoadmap saved = requireSaved(userId, roadmapId);
        Roadmap roadmap = saved.getRoadmap();
        roadmap.getNodes().forEach(n -> n.getResources().size()); // initialize inside tx
        List<Long> completedIds = nodeProgress.findCompletedNodeIds(userId, roadmapId);
        return new RoadmapProgressResponse(
                mapper.toResponse(roadmap),
                RoadmapProgressResponse.Progress.of(completedIds, roadmap.getNodes().size()));
    }

    /** Adds a shared roadmap (by share token) to the user's library. Idempotent. */
    @Transactional
    public RoadmapProgressResponse saveByShareToken(Long userId, String shareToken) {
        Roadmap roadmap = roadmaps.findByShareToken(shareToken.trim())
                .orElseThrow(() -> new NotFoundException("No roadmap found for share token " + shareToken));
        saveIfAbsent(userId, roadmap);
        return getWithProgress(userId, roadmap.getId());
    }

    /** Called after generation when the request carries a valid JWT. Idempotent. */
    @Transactional
    public void saveIfAbsent(Long userId, Roadmap roadmap) {
        if (savedRoadmaps.existsByUserIdAndRoadmapId(userId, roadmap.getId())) {
            return;
        }
        AppUser user = users.getReferenceById(userId);
        try {
            savedRoadmaps.saveAndFlush(new SavedRoadmap(user, roadmap));
        } catch (DataIntegrityViolationException ignored) {
            // Concurrent save of the same roadmap by the same user — already in the library.
        }
    }

    @Transactional
    public void removeFromLibrary(Long userId, Long roadmapId) {
        SavedRoadmap saved = requireSaved(userId, roadmapId);
        nodeProgress.deleteAllForUserAndRoadmap(userId, roadmapId);
        savedRoadmaps.delete(saved);
    }

    @Transactional
    public RoadmapProgressResponse.Progress setNodeProgress(Long userId, Long roadmapId, Long nodeId,
                                                            boolean completed) {
        SavedRoadmap saved = requireSaved(userId, roadmapId);
        RoadmapNode node = saved.getRoadmap().getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Node " + nodeId + " does not belong to roadmap " + roadmapId));

        if (completed) {
            if (nodeProgress.findByUserIdAndNodeId(userId, nodeId).isEmpty()) {
                nodeProgress.save(new NodeProgress(users.getReferenceById(userId), node));
            }
        } else {
            nodeProgress.findByUserIdAndNodeId(userId, nodeId).ifPresent(nodeProgress::delete);
        }

        return RoadmapProgressResponse.Progress.of(
                nodeProgress.findCompletedNodeIds(userId, roadmapId),
                saved.getRoadmap().getNodes().size());
    }

    private SavedRoadmap requireSaved(Long userId, Long roadmapId) {
        return savedRoadmaps.findByUserIdAndRoadmapId(userId, roadmapId)
                .orElseThrow(() -> new NotFoundException("Roadmap " + roadmapId + " is not in your library"));
    }

    private SavedRoadmapSummary toSummary(Long userId, SavedRoadmap saved) {
        Roadmap roadmap = saved.getRoadmap();
        return new SavedRoadmapSummary(
                roadmap.getId(),
                roadmap.getTopic(),
                roadmap.getTitle(),
                roadmap.getExperienceLevel(),
                roadmap.getGoal(),
                roadmap.getEstimatedTotalMinutes(),
                roadmap.getShareToken(),
                saved.getSavedAt(),
                nodeProgress.countByUserIdAndNodeRoadmapId(userId, roadmap.getId()),
                roadmap.getNodes().size());
    }
}
