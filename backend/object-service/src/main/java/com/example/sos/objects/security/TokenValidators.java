package com.example.sos.objects.security;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/**
 * Regras de validação aplicadas além da assinatura: expiração (sem tolerância de relógio),
 * emissor, audiência e presença de exp e sub.
 */
public final class TokenValidators {

    private TokenValidators() {
    }

    public static OAuth2TokenValidator<Jwt> create(String issuer, Collection<String> allowedAudiences) {
        return new DelegatingOAuth2TokenValidator<>(
                // O padrão do Spring tolera 60 s após a expiração; aqui a tolerância é zero.
                new JwtTimestampValidator(Duration.ZERO),
                new JwtIssuerValidator(issuer),
                new AudienceValidator(allowedAudiences),
                // O validador de timestamp aceita token sem exp; exigimos explicitamente.
                new JwtClaimValidator<Object>("exp", Objects::nonNull),
                new JwtClaimValidator<Object>("sub", Objects::nonNull));
    }
}
