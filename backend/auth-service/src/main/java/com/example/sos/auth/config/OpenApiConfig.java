package com.example.sos.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI authOpenApi() {
        return new OpenAPI().info(new Info()
                .title("auth-service")
                .version("0.1.0")
                .description("Cadastro de usuários, login e emissão de JWT (RS256, até 30 minutos). "
                        + "Use o accessToken retornado no login como Bearer nos demais serviços."));
    }
}
