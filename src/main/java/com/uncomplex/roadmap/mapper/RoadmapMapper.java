package com.uncomplex.roadmap.mapper;

import com.uncomplex.roadmap.dto.RoadmapResponse;
import com.uncomplex.roadmap.entity.NodeResource;
import com.uncomplex.roadmap.entity.Roadmap;
import com.uncomplex.roadmap.entity.RoadmapNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoadmapMapper {

    private static final String PUBLIC_PATH = "/api/roadmaps/public/";

    public RoadmapResponse toResponse(Roadmap roadmap) {
        List<RoadmapResponse.NodeResponse> nodes = roadmap.getNodes().stream()
                .map(this::toNodeResponse)
                .toList();

        return new RoadmapResponse(
                roadmap.getId(),
                roadmap.getShareToken(),
                PUBLIC_PATH + roadmap.getShareToken(),
                roadmap.getTopic(),
                roadmap.getTitle(),
                roadmap.getSummary(),
                roadmap.getExperienceLevel(),
                roadmap.getGoal(),
                roadmap.getEstimatedTotalMinutes(),
                roadmap.getCreatedAt(),
                nodes);
    }

    private RoadmapResponse.NodeResponse toNodeResponse(RoadmapNode node) {
        List<RoadmapResponse.ResourceResponse> resources = node.getResources().stream()
                .map(this::toResourceResponse)
                .toList();

        return new RoadmapResponse.NodeResponse(
                node.getId(),
                node.getName(),
                node.getDescription(),
                node.getReason(),
                node.getDifficulty(),
                node.getEstimatedMinutes(),
                node.getOrderIndex(),
                resources);
    }

    private RoadmapResponse.ResourceResponse toResourceResponse(NodeResource resource) {
        return new RoadmapResponse.ResourceResponse(
                resource.getTitle(),
                resource.getUrl(),
                resource.getSourceType(),
                resource.getCredibilityReason(),
                resource.getReachable());
    }
}
