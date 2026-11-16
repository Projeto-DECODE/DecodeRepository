package com.example.sos.bucket.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class TokenValidatorsTest {

    private final OAuth2TokenValidator<Jwt> validator = TokenValidators.create("sos-auth-service", List.of("sos-api"));

    private Jwt.Builder base() {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuer("sos-auth-service")
                .subject("2f1c4c0a-6d1e-4b6a-9c52-0a8e0a1c0001")
                .audience(List.of("sos-api"))
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(600));
    }

    @Test
    void acceptsValidToken() {
        assertFalse(validator.validate(base().build()).hasErrors());
    }

    @Test
    void rejectsExpiredTokenWithoutClockSkew() {
        Jwt expired = base().issuedAt(Instant.now().minusSeconds(120)).expiresAt(Instant.now().minusSeconds(5)).build();
        assertTrue(validator.validate(expired).hasErrors());
    }

    @Test
    void rejectsWrongIssuer() {
        assertTrue(validator.validate(base().issuer("someone-else").build()).hasErrors());
    }

    @Test
    void rejectsServiceAudience() {
        assertTrue(validator.validate(base().audience(List.of("sos-internal")).build()).hasErrors());
    }

    @Test
    void rejectsMissingAudience() {
        Jwt noAudience = Jwt.withTokenValue("t").header("alg", "RS256")
                .issuer("sos-auth-service").subject("x")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        assertTrue(validator.validate(noAudience).hasErrors());
    }

    @Test
    void rejectsMissingExpiration() {
        Jwt noExp = Jwt.withTokenValue("t").header("alg", "RS256")
                .issuer("sos-auth-service").subject("x").audience(List.of("sos-api"))
                .issuedAt(Instant.now()).claim("scope", "api").build();
        assertTrue(validator.validate(noExp).hasErrors());
    }
}
