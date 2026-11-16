package com.example.sos.objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sos.objects.client.BucketInfo;
import com.example.sos.objects.client.BucketServiceClient;
import com.example.sos.objects.exception.BucketNotFoundException;
import com.example.sos.objects.exception.DependencyUnavailableException;
import com.example.sos.objects.security.TokenValidators;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.mock.web.MockMultipartFile;
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
 * Integração com PostgreSQL real e disco real. O bucket-service é simulado pelo bean
 * BucketServiceClient (a comunicação HTTP real é coberta por BucketServiceClientTest e pelo fluxo
 * end-to-end em docs/api-examples.md). Exige Docker: roda em `mvn verify`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ObjectApiIT {

    static final KeyPair KEYS = TestTokens.newKeyPair();
    static Path storageRoot;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        storageRoot = Files.createTempDirectory("sos-objects");
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("sos.security.jwks-uri", () -> "http://localhost:9/jwks.json");
        registry.add("sos.security.issuer", () -> TestTokens.ISSUER);
        registry.add("sos.clients.bucket-service-url", () -> "http://localhost:9");
        registry.add("sos.storage.root", storageRoot::toString);
        registry.add("sos.storage.max-file-size", () -> "1KB");
    }

    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
            decoder.setJwtValidator(TokenValidators.create(TestTokens.ISSUER, List.of("sos-api", "sos-internal")));
            return decoder;
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean BucketServiceClient bucketClient;

    private final TestTokens tokens = new TestTokens(KEYS);

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    /** Faz o bucket-service simulado reconhecer o dono (o token é o do próprio usuário). */
    private UUID bucketOwnedBy(String userToken) {
        UUID bucketId = UUID.randomUUID();
        when(bucketClient.fetchBucket(bucketId, userToken)).thenReturn(new BucketInfo(bucketId, "docs", "ACTIVE"));
        return bucketId;
    }

    private String upload(UUID bucketId, String userToken, String name, byte[] content) throws Exception {
        String body = mvc.perform(multipart("/api/buckets/{id}/objects", bucketId)
                        .file(file(name, "text/plain", content))
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    // ---------- autenticação ----------

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/buckets/{id}/objects", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/buckets/{id}/objects", UUID.randomUUID()).file(file("a.txt", "text/plain", new byte[] {1})))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        String expired = tokens.mint(UUID.randomUUID().toString(), "sos-api", "api",
                Instant.now().minus(Duration.ofHours(1)), Duration.ofMinutes(15));
        mvc.perform(get("/api/buckets/{id}/objects", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsUnauthorized() throws Exception {
        TestTokens attacker = new TestTokens(TestTokens.newKeyPair());
        mvc.perform(get("/api/buckets/{id}/objects", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(attacker.user(UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceTokenCannotUseTheUserApi() throws Exception {
        mvc.perform(get("/api/buckets/{id}/objects", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.service("bucket-service"))))
                .andExpect(status().isForbidden());
    }

    // ---------- upload, metadados, download, exclusão ----------

    @Test
    void fullLifecycleUploadMetadataDownloadDelete() throws Exception {
        String token = tokens.user(UUID.randomUUID());
        UUID bucketId = bucketOwnedBy(token);
        byte[] content = "olá, mundo".getBytes();

        String objectId = upload(bucketId, token, "saudacao.txt", content);

        mvc.perform(get("/api/buckets/{b}/objects/{o}", bucketId, objectId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("saudacao.txt"))
                .andExpect(jsonPath("$.sizeBytes").value(content.length))
                .andExpect(jsonPath("$.bucketId").value(bucketId.toString()))
                .andExpect(jsonPath("$.sha256").isNotEmpty())
                .andExpect(jsonPath("$.storageKey").doesNotExist());

        byte[] downloaded = mvc.perform(get("/api/buckets/{b}/objects/{o}/download", bucketId, objectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(content, downloaded);

        mvc.perform(delete("/api/buckets/{b}/objects/{o}", bucketId, objectId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/buckets/{b}/objects/{o}", bucketId, objectId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadHeaderNeverCarriesTheClientFilenameUnsanitized() throws Exception {
        String token = tokens.user(UUID.randomUUID());
        UUID bucketId = bucketOwnedBy(token);
        String objectId = upload(bucketId, token, "../../etc/passwd", "x".getBytes());

        String disposition = mvc.perform(get("/api/buckets/{b}/objects/{o}/download", bucketId, objectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertEquals(true, disposition.startsWith("attachment"));
        assertEquals(false, disposition.contains(".."));
        assertEquals(false, disposition.contains("/"));
    }

    @Test
    void uploadPathTraversalFilenameIsOnlyMetadataAndNothingEscapesTheRoot() throws Exception {
        String token = tokens.user(UUID.randomUUID());
        UUID bucketId = bucketOwnedBy(token);

        upload(bucketId, token, "../../../../tmp/pwned.txt", "x".getBytes());

        try (var walk = Files.walk(storageRoot)) {
            long escaped = walk.filter(p -> p.getFileName().toString().contains("pwned")).count();
            assertEquals(0, escaped, "o nome do cliente nunca pode virar caminho físico");
        }
        assertEquals(false, Files.exists(Path.of("/tmp/pwned.txt")));
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        String token = tokens.user(UUID.randomUUID());
        UUID bucketId = bucketOwnedBy(token);

        mvc.perform(multipart("/api/buckets/{id}/objects", bucketId)
                        .file(file("vazio.txt", "text/plain", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));
    }

    @Test
    void fileAboveTheConfiguredLimitIsRejectedWith413() throws Exception {
        String token = tokens.user(UUID.randomUUID());
        UUID bucketId = bucketOwnedBy(token);

        mvc.perform(multipart("/api/buckets/{id}/objects", bucketId)
                        .file(file("grande.bin", "application/octet-stream", new byte[2048]))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void listIsPaginatedAndScopedToTheBucket() throws Exception {
        String token = tokens.user(UUID.randomUUID());
        UUID bucketId = bucketOwnedBy(token);
        UUID otherBucket = bucketOwnedBy(token);
        upload(bucketId, token, "a.txt", "a".getBytes());
        upload(bucketId, token, "b.txt", "b".getBytes());
        upload(otherBucket, token, "c.txt", "c".getBytes());

        mvc.perform(get("/api/buckets/{id}/objects", bucketId).param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    // ---------- autorização (IDOR/BOLA) ----------

    @Test
    void anotherUsersBucketIsNotFoundForEveryOperation() throws Exception {
        String bobToken = tokens.user(UUID.randomUUID());
        UUID aliceBucket = UUID.randomUUID();
        // O bucket-service responde 404 quando o bucket não é do usuário do token.
        when(bucketClient.fetchBucket(eq(aliceBucket), any())).thenThrow(new BucketNotFoundException());

        mvc.perform(get("/api/buckets/{b}/objects", aliceBucket).header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/buckets/{b}/objects", aliceBucket)
                        .file(file("x.txt", "text/plain", new byte[] {1}))
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/buckets/{b}/objects/{o}", aliceBucket, UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/buckets/{b}/objects/{o}/download", aliceBucket, UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/buckets/{b}/objects/{o}", aliceBucket, UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void objectFromAnotherBucketOfTheSameUserIsNotFound() throws Exception {
        String token = tokens.user(UUID.randomUUID());
        UUID bucketA = bucketOwnedBy(token);
        UUID bucketB = bucketOwnedBy(token);
        String objectInA = upload(bucketA, token, "a.txt", "a".getBytes());

        mvc.perform(get("/api/buckets/{b}/objects/{o}", bucketB, objectInA).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OBJECT_NOT_FOUND"));
        mvc.perform(get("/api/buckets/{b}/objects/{o}/download", bucketB, objectInA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/buckets/{b}/objects/{o}", bucketB, objectInA).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        // O objeto original continua intacto.
        mvc.perform(get("/api/buckets/{b}/objects/{o}", bucketA, objectInA).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void uploadToABucketBeingDeletedConflicts() throws Exception {
        String token = tokens.user(UUID.randomUUID());
        UUID bucketId = UUID.randomUUID();
        when(bucketClient.fetchBucket(bucketId, token)).thenReturn(new BucketInfo(bucketId, "docs", "DELETING"));

        mvc.perform(multipart("/api/buckets/{id}/objects", bucketId)
                        .file(file("x.txt", "text/plain", new byte[] {1}))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUCKET_NOT_WRITABLE"));
    }

    @Test
    void failsClosedWhenTheBucketServiceIsUnavailable() throws Exception {
        String token = tokens.user(UUID.randomUUID());
        UUID bucketId = UUID.randomUUID();
        when(bucketClient.fetchBucket(bucketId, token)).thenThrow(new DependencyUnavailableException("down"));

        mvc.perform(get("/api/buckets/{b}/objects", bucketId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    // ---------- rota interna ----------

    @Test
    void internalCountRequiresAServiceToken() throws Exception {
        UUID bucketId = bucketOwnedBy(tokens.user(UUID.randomUUID()));

        mvc.perform(get("/internal/buckets/{b}/objects/count", bucketId))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/buckets/{b}/objects/count", bucketId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.user(UUID.randomUUID()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/buckets/{b}/objects/count", bucketId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.service("bucket-service"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void internalCountReflectsUploadsAndDeletes() throws Exception {
        String token = tokens.user(UUID.randomUUID());
        UUID bucketId = bucketOwnedBy(token);
        String serviceToken = tokens.service("bucket-service");
        String objectId = upload(bucketId, token, "a.txt", "a".getBytes());

        mvc.perform(get("/internal/buckets/{b}/objects/count", bucketId).header(HttpHeaders.AUTHORIZATION, bearer(serviceToken)))
                .andExpect(jsonPath("$.count").value(1));

        mvc.perform(delete("/api/buckets/{b}/objects/{o}", bucketId, objectId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/internal/buckets/{b}/objects/count", bucketId).header(HttpHeaders.AUTHORIZATION, bearer(serviceToken)))
                .andExpect(jsonPath("$.count").value(0));
    }
}
