package com.example.sos.objects.security;

import com.example.sos.objects.exception.InvalidTokenSubjectException;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Quem está chamando: identidade e token, ambos extraídos do JWT já validado.
 * O token é encaminhado ao bucket-service, que decide se o usuário pode acessar o bucket.
 * O object-service não decide propriedade por conta própria.
 */
public record Caller(UUID userId, String bearerToken) {

    public static Caller from(Jwt jwt) {
        try {
            return new Caller(UUID.fromString(jwt.getSubject()), jwt.getTokenValue());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenSubjectException();
        }
    }
}
