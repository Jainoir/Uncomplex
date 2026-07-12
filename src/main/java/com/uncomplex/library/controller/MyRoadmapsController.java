package com.uncomplex.library.controller;

import com.uncomplex.library.dto.RoadmapProgressResponse;
import com.uncomplex.library.dto.SaveRoadmapRequest;
import com.uncomplex.library.dto.SavedRoadmapSummary;
import com.uncomplex.library.dto.UpdateProgressRequest;
import com.uncomplex.library.service.LibraryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The authenticated user's library. Shared roadmaps are immutable; what a user owns
 * is which roadmaps they follow and their per-node completion state.
 */
@RestController
@RequestMapping("/api/me/roadmaps")
public class MyRoadmapsController {

    private final LibraryService libraryService;

    public MyRoadmapsController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping
    public List<SavedRoadmapSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return libraryService.listFor(userId(jwt));
    }

    @GetMapping("/{roadmapId}")
    public RoadmapProgressResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long roadmapId) {
        return libraryService.getWithProgress(userId(jwt), roadmapId);
    }

    /** Save any shared roadmap (by share token) into the library. */
    @PostMapping
    public ResponseEntity<RoadmapProgressResponse> save(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody SaveRoadmapRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(libraryService.saveByShareToken(userId(jwt), request.shareToken()));
    }

    @DeleteMapping("/{roadmapId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long roadmapId) {
        libraryService.removeFromLibrary(userId(jwt), roadmapId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{roadmapId}/nodes/{nodeId}/progress")
    public RoadmapProgressResponse.Progress updateProgress(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable Long roadmapId,
                                                           @PathVariable Long nodeId,
                                                           @Valid @RequestBody UpdateProgressRequest request) {
        return libraryService.setNodeProgress(userId(jwt), roadmapId, nodeId, request.completed());
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
