package com.example.sos.auth.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Clientes de serviço autorizados a obter token via client credentials (clientId -> segredo). */
@ConfigurationProperties(prefix = "sos")
public record ServiceClientProperties(@DefaultValue Map<String, String> serviceClients) {

    public static final int MIN_SECRET_LENGTH = 32;

    public ServiceClientProperties {
        serviceClients.forEach((id, secret) -> {
            if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException(
                        "O segredo do cliente de serviço '" + id + "' deve ter ao menos "
                                + MIN_SECRET_LENGTH + " caracteres");
            }
        });
    }
}
