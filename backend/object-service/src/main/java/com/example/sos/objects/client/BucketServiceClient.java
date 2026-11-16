package com.example.sos.objects.client;

import com.example.sos.objects.config.ClientProperties;
import com.example.sos.objects.exception.BucketNotFoundException;
import com.example.sos.objects.exception.DependencyUnavailableException;
import com.example.sos.objects.exception.UpstreamUnauthorizedException;
import com.example.sos.objects.security.CorrelationIdFilter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Pergunta ao bucket-service se o bucket existe e pertence ao usuário, encaminhando o token DO USUÁRIO.
 * A regra de propriedade vive só no bucket-service; aqui ela não é duplicada.
 *
 * <p>Falha fechada: qualquer resposta inesperada ou erro de rede resulta em exceção, nunca em acesso.
 * Não há cache da resposta, para não manter uma permissão obsoleta.
 */
@Component
public class BucketServiceClient {

    private static final Logger log = LoggerFactory.getLogger(BucketServiceClient.class);

    private final RestClient restClient;

    public BucketServiceClient(ClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.bucketServiceUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * @throws BucketNotFoundException       bucket inexistente ou de outro usuário (404)
     * @throws UpstreamUnauthorizedException o bucket-service recusou o token (401/403)
     * @throws DependencyUnavailableException erro de rede, timeout ou resposta inesperada
     */
    public BucketInfo fetchBucket(UUID bucketId, String userBearerToken) {
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri("/api/buckets/{bucketId}", bucketId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + userBearerToken);
            String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (traceId != null) {
                request = request.header(CorrelationIdFilter.HEADER, traceId);
            }
            BucketInfo info = request.retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new BucketNotFoundException();
                    })
                    .onStatus(status -> status.value() == 401 || status.value() == 403, (req, res) -> {
                        throw new UpstreamUnauthorizedException();
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new DependencyUnavailableException(
                                "bucket-service respondeu " + res.getStatusCode().value());
                    })
                    .body(BucketInfo.class);
            if (info == null || info.id() == null) {
                throw new DependencyUnavailableException("bucket-service retornou resposta vazia");
            }
            return info;
        } catch (BucketNotFoundException | UpstreamUnauthorizedException | DependencyUnavailableException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("op=fetch-bucket result=error bucketId={} reason={}", bucketId, e.getClass().getSimpleName());
            throw new DependencyUnavailableException("bucket-service inacessível", e);
        }
    }
}
