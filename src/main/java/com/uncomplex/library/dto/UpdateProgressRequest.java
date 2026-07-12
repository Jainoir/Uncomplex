package com.uncomplex.library.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateProgressRequest(
        @NotNull Boolean completed
) {
}
