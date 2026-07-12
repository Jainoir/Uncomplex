package com.uncomplex.library.dto;

import com.uncomplex.roadmap.dto.RoadmapResponse;

import java.util.List;

/** A shared roadmap plus this user's personal progress overlay. */
public record RoadmapProgressResponse(
        RoadmapResponse roadmap,
        Progress progress
) {

    public record Progress(
            List<Long> completedNodeIds,
            long completedCount,
            int totalCount,
            int percent
    ) {

        public static Progress of(List<Long> completedNodeIds, int totalCount) {
            long completed = completedNodeIds.size();
            int percent = totalCount == 0 ? 0 : (int) Math.round(100.0 * completed / totalCount);
            return new Progress(completedNodeIds, completed, totalCount, percent);
        }
    }
}
