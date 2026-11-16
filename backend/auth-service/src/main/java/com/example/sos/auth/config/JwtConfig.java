package com.example.sos.auth.config;

import com.example.sos.auth.security.SigningKeyProvider;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

    @Bean
    SigningKeyProvider signingKeyProvider(JwtProperties properties) {
        if (properties.privateKeyPath() == null || properties.privateKeyPath().isBlank()) {
            throw new IllegalStateException("sos.jwt.private-key-path não configurado");
        }
        return SigningKeyProvider.fromPemFile(Path.of(properties.privateKeyPath()));
    }

    @Bean
    JwtEncoder jwtEncoder(SigningKeyProvider keyProvider) {
        return new NimbusJwtEncoder(
                new ImmutableJWKSet<SecurityContext>(new JWKSet(keyProvider.signingKey())));
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
