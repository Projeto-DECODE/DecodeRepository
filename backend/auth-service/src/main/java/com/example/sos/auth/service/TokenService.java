package com.example.sos.auth.service;

import com.example.sos.auth.config.JwtProperties;
import com.example.sos.auth.security.SigningKeyProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Emite tokens RS256. Dois tipos, separados por audiência e escopo:
 * usuário (aud=sos-api, scope=api) e serviço (aud=sos-internal, scope=internal).
 */
@Service
public class TokenService {

    public static final String SCOPE_API = "api";
    public static final String SCOPE_INTERNAL = "internal";

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final SigningKeyProvider keyProvider;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, JwtProperties properties, SigningKeyProvider keyProvider,
            Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.keyProvider = keyProvider;
        this.clock = clock;
    }

    public IssuedToken issueUserToken(UUID userId) {
        return issue(userId.toString(), properties.userAudience(), SCOPE_API, properties.userTtl());
    }

    public IssuedToken issueServiceToken(String clientId) {
        return issue(clientId, properties.internalAudience(), SCOPE_INTERNAL, properties.serviceTtl());
    }

    private IssuedToken issue(String subject, String audience, String scope, Duration configuredTtl) {
        // Segunda barreira: mesmo que a configuração seja alterada, nada passa de 30 minutos.
        Duration ttl = configuredTtl.compareTo(JwtProperties.MAX_TTL) > 0 ? JwtProperties.MAX_TTL : configuredTtl;
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(ttl);

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyProvider.keyId()).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("scope", scope)
                .build();

        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, ttl.toSeconds(), expiresAt);
    }
}
