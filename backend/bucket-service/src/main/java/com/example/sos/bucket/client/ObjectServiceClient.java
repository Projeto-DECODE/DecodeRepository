package com.example.sos.bucket.client;

import com.example.sos.bucket.config.ClientProperties;
import com.example.sos.bucket.exception.DependencyUnavailableException;
import com.example.sos.bucket.security.CorrelationIdFilter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente HTTP do object-service. Usa o token de SERVIÇO (não o do usuário) porque a rota consultada
 * é interna. Qualquer falha vira {@link DependencyUnavailableException}: nunca se presume bucket vazio.
 */
@Component
public class ObjectServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ObjectServiceClient.class);

    record CountResponse(long count) {
    }

    private final RestClient restClient;
    private final ServiceTokenProvider tokenProvider;

    public ObjectServiceClient(ClientProperties properties, ServiceTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.objectServiceUrl())
                .requestFactory(factory)
                .build();
    }

    /** Quantidade de objetos no bucket, segundo o object-service. */
    public long countObjects(UUID bucketId) {
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri("/internal/buckets/{bucketId}/objects/count", bucketId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.token());
            String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (traceId != null) {
                request = request.header(CorrelationIdFilter.HEADER, traceId);
            }
            CountResponse response = request.retrieve().body(CountResponse.class);
            if (response == null || response.count() < 0) {
                throw new DependencyUnavailableException("object-service retornou contagem inválida");
            }
            return response.count();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                tokenProvider.invalidate();
            }
            log.warn("op=count-objects result=error bucketId={} status={}", bucketId, e.getStatusCode().value());
            throw new DependencyUnavailableException("object-service respondeu " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            log.warn("op=count-objects result=error bucketId={} reason={}", bucketId, e.getClass().getSimpleName());
            throw new DependencyUnavailableException("object-service inacessível", e);
        }
    }
}
