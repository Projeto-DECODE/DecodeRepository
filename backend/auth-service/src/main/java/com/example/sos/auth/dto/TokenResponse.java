package com.example.sos.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Validade do token em segundos", example = "900") long expiresIn) {

    public static TokenResponse bearer(String accessToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
