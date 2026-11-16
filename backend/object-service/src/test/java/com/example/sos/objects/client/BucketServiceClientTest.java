package com.example.sos.objects.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sos.objects.config.ClientProperties;
import com.example.sos.objects.exception.BucketNotFoundException;
import com.example.sos.objects.exception.DependencyUnavailableException;
import com.example.sos.objects.exception.UpstreamUnauthorizedException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Usa um servidor HTTP real (JDK) no lugar do bucket-service, para testar o cliente de verdade:
 * cabeçalhos encaminhados, mapeamento de status e comportamento de falha fechada.
 */
class BucketServiceClientTest {

    private HttpServer server;
    private final AtomicReference<String> receivedAuth = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "";
    private volatile long delayMillis = 0;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/buckets/", exchange -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private BucketServiceClient client(Duration readTimeout) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        return new BucketServiceClient(new ClientProperties(url, Duration.ofSeconds(1), readTimeout));
    }

    @Test
    void forwardsTheUsersTokenAndParsesTheBucket() {
        UUID id = UUID.randomUUID();
        responseBody = "{\"id\":\"" + id + "\",\"name\":\"docs\",\"status\":\"ACTIVE\",\"createdAt\":\"x\"}";

        BucketInfo info = client(Duration.ofSeconds(2)).fetchBucket(id, "token-do-usuario");

        assertEquals("Bearer token-do-usuario", receivedAuth.get());
        assertEquals(id, info.id());
        assertTrue(info.isActive());
    }

    @Test
    void notFoundMapsToBucketNotFound() {
        responseStatus = 404;
        assertThrows(BucketNotFoundException.class,
                () -> client(Duration.ofSeconds(2)).fetchBucket(UUID.randomUUID(), "t"));
    }

    @Test
    void unauthorizedAndForbiddenMapToUpstreamUnauthorized() {
        for (int status : new int[] {401, 403}) {
            responseStatus = status;
            assertThrows(UpstreamUnauthorizedException.class,
                    () -> client(Duration.ofSeconds(2)).fetchBucket(UUID.randomUUID(), "t"));
        }
    }

    @Test
    void serverErrorFailsClosed() {
        responseStatus = 500;
        assertThrows(DependencyUnavailableException.class,
                () -> client(Duration.ofSeconds(2)).fetchBucket(UUID.randomUUID(), "t"));
    }

    @Test
    void emptySuccessResponseFailsClosed() {
        responseStatus = 200;
        responseBody = "";
        assertThrows(DependencyUnavailableException.class,
                () -> client(Duration.ofSeconds(2)).fetchBucket(UUID.randomUUID(), "t"));
    }

    @Test
    void timeoutFailsClosed() {
        delayMillis = 1500;
        responseBody = "{\"id\":\"" + UUID.randomUUID() + "\",\"name\":\"d\",\"status\":\"ACTIVE\"}";
        assertThrows(DependencyUnavailableException.class,
                () -> client(Duration.ofMillis(300)).fetchBucket(UUID.randomUUID(), "t"));
    }

    @Test
    void connectionRefusedFailsClosed() {
        server.stop(0);
        assertThrows(DependencyUnavailableException.class,
                () -> client(Duration.ofSeconds(1)).fetchBucket(UUID.randomUUID(), "t"));
    }
}
