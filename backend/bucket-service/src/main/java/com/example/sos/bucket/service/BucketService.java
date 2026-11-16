package com.example.sos.bucket.service;

import com.example.sos.bucket.client.ObjectServiceClient;
import com.example.sos.bucket.domain.Bucket;
import com.example.sos.bucket.domain.BucketStatus;
import com.example.sos.bucket.exception.BucketNameAlreadyExistsException;
import com.example.sos.bucket.exception.BucketNotEmptyException;
import com.example.sos.bucket.exception.BucketNotFoundException;
import com.example.sos.bucket.exception.DependencyUnavailableException;
import com.example.sos.bucket.repository.BucketRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Regras de bucket. Toda consulta por ID passa pelo proprietário (ownerUserId vindo do JWT),
 * então um ID alheio é indistinguível de um ID inexistente (404).
 *
 * <p>Este serviço não é transacional de propósito: a exclusão faz uma chamada HTTP ao object-service
 * e não deve segurar uma transação de banco aberta durante ela. Cada transição de estado é atômica
 * e confirmada individualmente pelo repositório.
 */
@Service
public class BucketService {

    private static final Logger log = LoggerFactory.getLogger(BucketService.class);

    private final BucketRepository repository;
    private final ObjectServiceClient objectClient;
    private final Clock clock;

    public BucketService(BucketRepository repository, ObjectServiceClient objectClient, Clock clock) {
        this.repository = repository;
        this.objectClient = objectClient;
        this.clock = clock;
    }

    public Bucket create(UUID ownerUserId, String name) {
        if (repository.existsByOwnerUserIdAndName(ownerUserId, name)) {
            throw new BucketNameAlreadyExistsException();
        }
        try {
            Bucket saved = repository.saveAndFlush(new Bucket(name, ownerUserId, clock.instant()));
            log.info("op=create-bucket result=success bucketId={} ownerUserId={}", saved.getId(), ownerUserId);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Criações simultâneas com o mesmo nome: a restrição UNIQUE do banco decide.
            throw new BucketNameAlreadyExistsException();
        }
    }

    public List<Bucket> list(UUID ownerUserId) {
        return repository.findByOwnerUserIdOrderByCreatedAtAsc(ownerUserId);
    }

    public Bucket get(UUID ownerUserId, UUID bucketId) {
        return repository.findByIdAndOwnerUserId(bucketId, ownerUserId)
                .orElseThrow(BucketNotFoundException::new);
    }

    /**
     * Exclusão em três passos para reduzir a corrida com uploads:
     * 1) marca DELETING (o object-service passa a recusar uploads);
     * 2) pergunta ao object-service quantos objetos existem;
     * 3) se zero, exclui; senão volta para ACTIVE.
     * Uma nova chamada de DELETE sobre um bucket preso em DELETING retoma o fluxo.
     */
    public void delete(UUID ownerUserId, UUID bucketId) {
        get(ownerUserId, bucketId); // 404 se inexistente ou de outro usuário

        repository.transition(bucketId, BucketStatus.ACTIVE, BucketStatus.DELETING);

        long objectCount;
        try {
            objectCount = objectClient.countObjects(bucketId);
        } catch (DependencyUnavailableException e) {
            restoreActive(bucketId);
            throw e;
        }

        if (objectCount > 0) {
            restoreActive(bucketId);
            log.info("op=delete-bucket result=rejected reason=not-empty bucketId={}", bucketId);
            throw new BucketNotEmptyException();
        }

        int removed = repository.deleteByIdAndStatus(bucketId, BucketStatus.DELETING);
        log.info("op=delete-bucket result={} bucketId={}", removed == 1 ? "success" : "noop", bucketId);
    }

    private void restoreActive(UUID bucketId) {
        repository.transition(bucketId, BucketStatus.DELETING, BucketStatus.ACTIVE);
    }
}
