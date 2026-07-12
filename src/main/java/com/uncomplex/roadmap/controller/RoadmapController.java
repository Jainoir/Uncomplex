package com.uncomplex.roadmap.controller;

import com.uncomplex.library.service.LibraryService;
import com.uncomplex.roadmap.dto.GenerateRoadmapRequest;
import com.uncomplex.roadmap.dto.RoadmapResponse;
import com.uncomplex.roadmap.entity.Roadmap;
import com.uncomplex.roadmap.mapper.RoadmapMapper;
import com.uncomplex.roadmap.service.RoadmapService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/roadmaps")
public class RoadmapController {

    private final RoadmapService service;
    private final RoadmapMapper mapper;
    private final LibraryService libraryService;

    public RoadmapController(RoadmapService service, RoadmapMapper mapper, LibraryService libraryService) {
        this.service = service;
        this.mapper = mapper;
        this.libraryService = libraryService;
    }

    /** Generation stays anonymous-friendly; with a valid JWT the result also lands in the user's library. */
    @PostMapping
    public ResponseEntity<RoadmapResponse> generate(@Valid @RequestBody GenerateRoadmapRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        Roadmap roadmap = service.getOrGenerate(request.topic(), request.experienceLevel(), request.goal());
        if (jwt != null) {
            libraryService.saveIfAbsent(Long.valueOf(jwt.getSubject()), roadmap);
        }
        RoadmapResponse body = mapper.toResponse(roadmap);
        return ResponseEntity
                .created(URI.create(body.shareUrl()))
                .body(body);
    }

    @GetMapping("/public/{shareToken}")
    public RoadmapResponse getShared(@PathVariable String shareToken) {
        return mapper.toResponse(service.getByShareToken(shareToken));
    }
}
