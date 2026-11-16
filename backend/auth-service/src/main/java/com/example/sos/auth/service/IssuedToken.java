package com.example.sos.auth.service;

import java.time.Instant;

public record IssuedToken(String value, long expiresInSeconds, Instant expiresAt) {
}
