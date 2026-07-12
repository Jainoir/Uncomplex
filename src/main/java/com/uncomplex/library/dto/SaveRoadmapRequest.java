package com.uncomplex.library.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveRoadmapRequest(
        @NotBlank String shareToken
) {
}
