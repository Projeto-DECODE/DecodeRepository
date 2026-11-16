package com.example.sos.bucket.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Destinos e credenciais das chamadas HTTP que o bucket-service faz a outros serviços. */
@ConfigurationProperties(prefix = "sos.clients")
public record ClientProperties(
        String objectServiceUrl,
        String authServiceUrl,
        String clientId,
        String clientSecret,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout) {
}
