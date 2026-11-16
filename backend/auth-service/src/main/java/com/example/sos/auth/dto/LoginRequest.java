package com.example.sos.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "maria@example.com")
        @NotBlank @Size(max = 254) String email,

        @Schema(example = "uma-senha-forte-123")
        @NotBlank @Size(max = 128) String password) {
}
