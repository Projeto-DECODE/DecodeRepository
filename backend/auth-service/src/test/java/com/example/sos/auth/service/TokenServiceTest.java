package com.example.sos.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sos.auth.TestKeys;
import com.example.sos.auth.config.JwtProperties;
import com.example.sos.auth.security.SigningKeyProvider;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenServiceTest {

    private static final JwtProperties PROPS = new JwtProperties(
            "sos-auth-service", "sos-api", "sos-internal",
            Duration.ofMinutes(15), Duration.ofMinutes(5), null);

    private KeyPair keyPair;
    private NimbusJwtDecoder decoder;

    @BeforeEach
    void setUp() {
        keyPair = TestKeys.generate();
        decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
    }

    private TokenService serviceFor(KeyPair pair, Clock clock) {
        SigningKeyProvider provider = SigningKeyProvider.fromPrivateKey((RSAPrivateCrtKey) pair.getPrivate());
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableJWKSet<SecurityContext>(new JWKSet(provider.signingKey())));
        return new TokenService(encoder, PROPS, provider, clock);
    }

    @Test
    void userTokenHasRequiredClaimsAndNothingElse() {
        UUID userId = UUID.randomUUID();
        IssuedToken issued = serviceFor(keyPair, Clock.systemUTC()).issueUserToken(userId);

        Jwt jwt = decoder.decode(issued.value());

        assertEquals(userId.toString(), jwt.getSubject());
        assertEquals("sos-auth-service", jwt.getClaimAsString("iss"));
        assertEquals(java.util.List.of("sos-api"), jwt.getAudience());
        assertEquals("api", jwt.getClaimAsString("scope"));
        assertNotNull(jwt.getId());
        assertNotNull(jwt.getIssuedAt());
        assertNotNull(jwt.getExpiresAt());
        assertNotNull(jwt.getHeaders().get("kid"));
        // Nenhum dado pessoal ou segredo no token.
        assertEquals(Set.of("iss", "sub", "aud", "iat", "exp", "jti", "scope"), jwt.getClaims().keySet());
    }

    @Test
    void tokenLifetimeNeverExceedsThirtyMinutes() {
        IssuedToken issued = serviceFor(keyPair, Clock.systemUTC()).issueUserToken(UUID.randomUUID());

        Jwt jwt = decoder.decode(issued.value());

        long lifetime = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds();
        assertTrue(lifetime <= Duration.ofMinutes(30).toSeconds());
        assertEquals(Duration.ofMinutes(15).toSeconds(), lifetime);
        assertEquals(lifetime, issued.expiresInSeconds());
    }

    @Test
    void expiredTokenIsRejected() {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), java.time.ZoneOffset.UTC);
        IssuedToken issued = serviceFor(keyPair, twoHoursAgo).issueUserToken(UUID.randomUUID());

        assertThrows(JwtException.class, () -> decoder.decode(issued.value()));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        KeyPair attacker = TestKeys.generate();
        IssuedToken forged = serviceFor(attacker, Clock.systemUTC()).issueUserToken(UUID.randomUUID());

        assertThrows(JwtException.class, () -> decoder.decode(forged.value()));
    }

    @Test
    void serviceTokenUsesInternalAudienceAndScope() {
        IssuedToken issued = serviceFor(keyPair, Clock.systemUTC()).issueServiceToken("bucket-service");

        Jwt jwt = decoder.decode(issued.value());

        assertEquals("bucket-service", jwt.getSubject());
        assertEquals(java.util.List.of("sos-internal"), jwt.getAudience());
        assertEquals("internal", jwt.getClaimAsString("scope"));
        assertFalse(jwt.getAudience().contains("sos-api"));
    }
}
