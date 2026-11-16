package com.example.sos.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sos.auth.domain.User;
import com.example.sos.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Teste de integração com PostgreSQL real (Testcontainers). Exige Docker: roda em `mvn verify`. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthApiIT {

    private static final String CLIENT_SECRET = "test-client-secret-with-at-least-32-chars!!";
    private static final KeyPair KEYS = TestKeys.generate();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        Path pem = TestKeys.writePrivatePem(KEYS, Files.createTempDirectory("sos-keys"));
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("sos.jwt.private-key-path", pem::toString);
        registry.add("sos.service-clients.bucket-service", () -> CLIENT_SECRET);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;

    private final NimbusJwtDecoder decoder =
            NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();

    private static String registerJson(String email) {
        return """
                {"name":"Maria Silva","email":"%s","password":"uma-senha-forte-123"}""".formatted(email);
    }

    private static String loginJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}""".formatted(email, password);
    }

    private MvcResult register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email))).andReturn();
    }

    @Test
    void registerReturnsCreatedWithoutPasswordOrHash() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("create@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("create@example.com"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerRejectsInvalidEmail() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("nao-e-um-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("email"));
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
        register("dup@example.com");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("DUP@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"));
    }

    @Test
    void passwordIsStoredAsArgon2idHash() throws Exception {
        register("hash@example.com");

        User stored = users.findByEmail("hash@example.com").orElseThrow();

        assertTrue(stored.getPasswordHash().startsWith("$argon2id$"));
        assertFalse(stored.getPasswordHash().contains("uma-senha-forte-123"));
    }

    @Test
    void loginIssuesJwtValidForAtMostThirtyMinutes() throws Exception {
        MvcResult registered = register("login@example.com");
        String userId = mapper.readTree(registered.getResponse().getContentAsString()).get("id").asText();

        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("login@example.com", "uma-senha-forte-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        Jwt jwt = decoder.decode(body.get("accessToken").asText());

        assertEquals(userId, jwt.getSubject());
        assertEquals("sos-api", jwt.getAudience().get(0));
        assertTrue(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).compareTo(Duration.ofMinutes(30)) <= 0);
    }

    @Test
    void wrongPasswordAndUnknownEmailLookIdentical() throws Exception {
        register("wrongpw@example.com");

        MvcResult wrongPassword = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("wrongpw@example.com", "senha-errada-123")))
                .andExpect(status().isUnauthorized()).andReturn();
        MvcResult unknownEmail = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("ghost@example.com", "senha-errada-123")))
                .andExpect(status().isUnauthorized()).andReturn();

        JsonNode a = mapper.readTree(wrongPassword.getResponse().getContentAsString());
        JsonNode b = mapper.readTree(unknownEmail.getResponse().getContentAsString());
        assertEquals("INVALID_CREDENTIALS", a.get("code").asText());
        assertEquals(a.get("code"), b.get("code"));
        assertEquals(a.get("message"), b.get("message"));
    }

    @Test
    void jwksExposesOnlyThePublicKey() throws Exception {
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").exists())
                .andExpect(jsonPath("$.keys[0].n").exists())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist());
    }

    @Test
    void serviceTokenIsIssuedForValidClientOnly() throws Exception {
        MvcResult ok = mvc.perform(post("/internal/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "bucket-service")
                        .param("client_secret", CLIENT_SECRET))
                .andExpect(status().isOk()).andReturn();
        Jwt jwt = decoder.decode(mapper.readTree(ok.getResponse().getContentAsString()).get("accessToken").asText());
        assertEquals("internal", jwt.getClaimAsString("scope"));
        assertEquals("sos-internal", jwt.getAudience().get(0));
        assertEquals("bucket-service", jwt.getSubject());

        mvc.perform(post("/internal/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "bucket-service")
                        .param("client_secret", "segredo-errado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CLIENT"));
    }

    @Test
    void unlistedRoutesAreDenied() throws Exception {
        mvc.perform(get("/api/qualquer-coisa")).andExpect(status().is4xxClientError());
    }
}
