package com.example.sos.auth.dto;

import com.example.sos.auth.domain.User;
import java.time.Instant;
import java.util.UUID;

/** Nunca inclui senha nem hash. */
public record UserResponse(UUID id, String name, String email, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }
}
