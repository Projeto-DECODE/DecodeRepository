package com.example.sos.bucket.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sos.security")
public record SecurityProperties(
        String jwksUri,
        String issuer,
        @DefaultValue("sos-api") List<String> allowedAudiences) {
}
