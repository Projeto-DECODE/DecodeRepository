package com.example.sos.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração de emissão de tokens. O limite de 30 minutos é imposto aqui, no construtor:
 * se a configuração ultrapassar o limite, a aplicação falha na inicialização.
 */
@ConfigurationProperties(prefix = "sos.jwt")
public record JwtProperties(
        @DefaultValue("sos-auth-service") String issuer,
        @DefaultValue("sos-api") String userAudience,
        @DefaultValue("sos-internal") String internalAudience,
        @DefaultValue("15m") Duration userTtl,
        @DefaultValue("5m") Duration serviceTtl,
        String privateKeyPath) {

    public static final Duration MAX_TTL = Duration.ofMinutes(30);

    public JwtProperties {
        requireValidTtl("sos.jwt.user-ttl", userTtl);
        requireValidTtl("sos.jwt.service-ttl", serviceTtl);
    }

    private static void requireValidTtl(String name, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalStateException(
                    name + " deve ser maior que zero e no máximo " + MAX_TTL.toMinutes() + " minutos");
        }
    }
}
