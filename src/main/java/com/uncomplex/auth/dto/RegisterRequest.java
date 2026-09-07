package com.uncomplex.auth.dto;

import com.uncomplex.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255)
        String email,

        /**
         * 72 bytes is BCrypt's hard limit, not a preference. Advertising 100 characters let
         * longer passwords past validation and into the encoder, which threw and returned a
         * 500 for input the API had accepted.
         */
        @NotBlank
        @Size(min = 8, message = "password must be at least 8 characters")
        @MaxUtf8Bytes(value = 72, message = "password must be at most 72 bytes; accented and non-Latin characters use more than one byte each")
        String password
) {
}
