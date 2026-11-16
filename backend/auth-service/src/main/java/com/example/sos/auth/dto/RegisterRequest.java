package com.example.sos.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "Maria Silva")
        @NotBlank @Size(max = 100) String name,

        @Schema(example = "maria@example.com")
        @NotBlank @Email @Size(max = 254) String email,

        @Schema(example = "uma-senha-forte-123")
        @NotBlank @Size(min = 8, max = 128) String password) {
}
