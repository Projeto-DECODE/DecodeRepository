package com.example.sos.auth.controller;

import com.example.sos.auth.security.SigningKeyProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "JWKS", description = "Chave pública para validação dos tokens")
public class JwksController {

    private final SigningKeyProvider keyProvider;

    public JwksController(SigningKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @Operation(summary = "Conjunto de chaves públicas (JWKS)",
            description = "Contém somente a chave pública. Consumido pelo bucket-service e pelo object-service.")
    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return keyProvider.publicJwkSet();
    }
}
