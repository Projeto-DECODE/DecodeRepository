package com.example.sos.bucket.security;

import com.example.sos.bucket.exception.InvalidTokenSubjectException;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** A identidade do usuário vem exclusivamente do JWT já validado, nunca de corpo, parâmetro ou header. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenSubjectException();
        }
    }
}
