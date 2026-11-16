package com.example.sos.auth.controller;

import com.example.sos.auth.dto.TokenResponse;
import com.example.sos.auth.exception.UnsupportedGrantTypeException;
import com.example.sos.auth.service.AuthService;
import com.example.sos.auth.service.IssuedToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token de serviço (client credentials) usado em chamadas entre serviços.
 * Emite aud=sos-internal e scope=internal, que NÃO são aceitos nas rotas /api/**.
 */
@RestController
@Tag(name = "Interno", description = "Uso exclusivo entre serviços")
public class InternalTokenController {

    private final AuthService authService;

    public InternalTokenController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Emite token de serviço (client credentials)")
    @PostMapping(value = "/internal/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public TokenResponse token(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret) {
        if (!"client_credentials".equals(grantType)) {
            throw new UnsupportedGrantTypeException();
        }
        IssuedToken token = authService.issueServiceToken(clientId, clientSecret);
        return TokenResponse.bearer(token.value(), token.expiresInSeconds());
    }
}
