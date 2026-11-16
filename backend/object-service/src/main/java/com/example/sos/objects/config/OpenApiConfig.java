package com.example.sos.objects.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI objectOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("object-service")
                        .version("0.1.0")
                        .description("Upload, download e gerenciamento de objetos dentro de buckets. "
                                + "Todas as operações exigem JWT emitido pelo auth-service. "
                                + "As rotas /internal/** são de uso exclusivo entre serviços."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
