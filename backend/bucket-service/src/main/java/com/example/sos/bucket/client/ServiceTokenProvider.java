package com.example.sos.bucket.client;

import com.example.sos.bucket.config.ClientProperties;
import com.example.sos.bucket.exception.DependencyUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Obtém (e mantém em cache) o token de serviço do bucket-service via client credentials no
 * auth-service. Esse token tem aud=sos-internal e scope=internal: serve apenas para rotas /internal/**.
 */
@Component
public class ServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenProvider.class);
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(30);

    record AuthTokenResponse(String accessToken, long expiresIn) {
    }

    record CachedToken(String value, Instant refreshAt) {
    }

    private final RestClient authClient;
    private final ClientProperties properties;
    private final Clock clock;
    private CachedToken cached;

    public ServiceTokenProvider(ClientProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.authClient = RestClient.builder()
                .baseUrl(properties.authServiceUrl())
                .requestFactory(factory)
                .build();
    }

    public synchronized String token() {
        if (cached == null || !clock.instant().isBefore(cached.refreshAt())) {
            cached = fetch();
        }
        return cached.value();
    }

    /** Descarta o token em cache (por exemplo, após receber 401 do serviço de destino). */
    public synchronized void invalidate() {
        cached = null;
    }

    private CachedToken fetch() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        try {
            AuthTokenResponse response = authClient.post()
                    .uri("/internal/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(AuthTokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new DependencyUnavailableException("auth-service retornou resposta vazia");
            }
            Instant refreshAt = clock.instant().plusSeconds(response.expiresIn()).minus(REFRESH_MARGIN);
            return new CachedToken(response.accessToken(), refreshAt);
        } catch (RestClientException e) {
            log.warn("op=fetch-service-token result=error reason={}", e.getClass().getSimpleName());
            throw new DependencyUnavailableException("não foi possível obter token de serviço", e);
        }
    }
}
