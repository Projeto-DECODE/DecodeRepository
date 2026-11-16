package com.example.sos.bucket;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sos.bucket.client.ObjectServiceClient;
import com.example.sos.bucket.exception.DependencyUnavailableException;
import com.example.sos.bucket.security.TokenValidators;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integração com PostgreSQL real. O JwtDecoder de teste valida com a chave pública local, mas usa as
 * mesmas regras de {@link TokenValidators} do serviço. A busca da chave via JWKS é coberta pelo fluxo
 * end-to-end com Docker Compose. Exige Docker: roda em `mvn verify`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BucketApiIT {

    static final KeyPair KEYS = TestTokens.newKeyPair();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("sos.security.jwks-uri", () -> "http://localhost:9/jwks.json");
        registry.add("sos.security.issuer", () -> TestTokens.ISSUER);
        registry.add("sos.clients.object-service-url", () -> "http://localhost:9");
        registry.add("sos.clients.auth-service-url", () -> "http://localhost:9");
        registry.add("sos.clients.client-secret", () -> "test-client-secret-with-at-least-32-chars!!");
    }

    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
            decoder.setJwtValidator(TokenValidators.create(TestTokens.ISSUER, List.of("sos-api")));
            return decoder;
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean ObjectServiceClient objectClient;

    private final TestTokens tokens = new TestTokens(KEYS);

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String createBucket(UUID user, String name) throws Exception {
        String body = mvc.perform(post("/api/buckets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    // ---------- autenticação ----------

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/buckets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void garbageTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/buckets").header(HttpHeaders.AUTHORIZATION, "Bearer isto.nao.e-um-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        String expired = tokens.mint(UUID.randomUUID().toString(), "sos-api", "api",
                Instant.now().minus(Duration.ofHours(1)), Duration.ofMinutes(15));
        mvc.perform(get("/api/buckets").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsUnauthorized() throws Exception {
        TestTokens attacker = new TestTokens(TestTokens.newKeyPair());
        mvc.perform(get("/api/buckets").header(HttpHeaders.AUTHORIZATION, bearer(attacker.user(UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceTokenCannotBeUsedOnUserApi() throws Exception {
        String serviceToken = tokens.mint("bucket-service", "sos-internal", "internal",
                Instant.now(), Duration.ofMinutes(5));
        mvc.perform(get("/api/buckets").header(HttpHeaders.AUTHORIZATION, bearer(serviceToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenWithoutApiScopeIsForbidden() throws Exception {
        String noScope = tokens.mint(UUID.randomUUID().toString(), "sos-api", "other",
                Instant.now(), Duration.ofMinutes(5));
        mvc.perform(get("/api/buckets").header(HttpHeaders.AUTHORIZATION, bearer(noScope)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---------- criação, listagem, consulta ----------

    @Test
    void ownerComesFromTheTokenEvenIfTheBodyTriesToSetIt() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        mvc.perform(post("/api/buckets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(alice)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"spoof-owner\",\"ownerUserId\":\"" + bob + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUserId").doesNotExist());

        mvc.perform(get("/api/buckets").header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(alice))))
                .andExpect(jsonPath("$[?(@.name=='spoof-owner')]").exists());
        mvc.perform(get("/api/buckets").header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(bob))))
                .andExpect(jsonPath("$[?(@.name=='spoof-owner')]").doesNotExist());
    }

    @Test
    void listReturnsOnlyTheCallersBuckets() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        createBucket(alice, "alice-one");
        createBucket(alice, "alice-two");
        createBucket(bob, "bob-one");

        mvc.perform(get("/api/buckets").header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(alice))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAndDeleteOfAnotherUsersBucketReturnNotFound() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        String aliceBucket = createBucket(alice, "alice-private");

        mvc.perform(get("/api/buckets/" + aliceBucket).header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(bob))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUCKET_NOT_FOUND"));
        mvc.perform(delete("/api/buckets/" + aliceBucket).header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(bob))))
                .andExpect(status().isNotFound());

        // O bucket segue intacto para o dono.
        mvc.perform(get("/api/buckets/" + aliceBucket).header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(alice))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ---------- validação e unicidade ----------

    @Test
    void duplicateNameForTheSameOwnerConflictsButAnotherOwnerMayReuseIt() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        createBucket(alice, "shared-name");

        mvc.perform(post("/api/buckets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(alice)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"shared-name\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUCKET_NAME_ALREADY_EXISTS"));

        createBucket(bob, "shared-name");
    }

    @Test
    void invalidNamesAreRejected() throws Exception {
        UUID alice = UUID.randomUUID();
        for (String invalid : List.of("AB", "UpperCase", "-starts-with-hyphen", "has space", "a".repeat(64))) {
            mvc.perform(post("/api/buckets")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(alice)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + invalid + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    // ---------- exclusão ----------

    @Test
    void emptyBucketIsDeleted() throws Exception {
        UUID alice = UUID.randomUUID();
        String id = createBucket(alice, "to-delete");
        when(objectClient.countObjects(UUID.fromString(id))).thenReturn(0L);

        mvc.perform(delete("/api/buckets/" + id).header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(alice))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/buckets/" + id).header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(alice))))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonEmptyBucketConflictsAndStaysActive() throws Exception {
        UUID alice = UUID.randomUUID();
        String id = createBucket(alice, "has-objects");
        when(objectClient.countObjects(UUID.fromString(id))).thenReturn(2L);

        mvc.perform(delete("/api/buckets/" + id).header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(alice))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUCKET_NOT_EMPTY"));
        mvc.perform(get("/api/buckets/" + id).header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(alice))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deleteFailsClosedWhenObjectServiceIsUnavailable() throws Exception {
        UUID alice = UUID.randomUUID();
        String id = createBucket(alice, "service-down");
        when(objectClient.countObjects(UUID.fromString(id))).thenThrow(new DependencyUnavailableException("down"));

        mvc.perform(delete("/api/buckets/" + id).header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(alice))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
        mvc.perform(get("/api/buckets/" + id).header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(alice))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
