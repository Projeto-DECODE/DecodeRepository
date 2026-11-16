package com.example.sos.objects.service;

import com.example.sos.objects.client.BucketInfo;
import com.example.sos.objects.client.BucketServiceClient;
import com.example.sos.objects.domain.StoredObject;
import com.example.sos.objects.dto.DownloadedObject;
import com.example.sos.objects.exception.BucketNotWritableException;
import com.example.sos.objects.exception.ObjectNotFoundException;
import com.example.sos.objects.exception.StorageException;
import com.example.sos.objects.repository.StoredObjectRepository;
import com.example.sos.objects.security.Caller;
import com.example.sos.objects.storage.FileSystemObjectStorage;
import com.example.sos.objects.storage.StoredFile;
import java.io.InputStream;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Regras de objeto. Toda operação começa perguntando ao bucket-service se o usuário pode acessar o
 * bucket (ele devolve 404 para bucket inexistente ou alheio). Depois, todo objeto é buscado por
 * (objectId, bucketId), nunca só por objectId.
 *
 * <p>Sem {@code @Transactional} de propósito: operações de disco e HTTP não devem segurar transação
 * de banco, e cada operação aqui faz no máximo uma escrita no banco.
 */
@Service
public class ObjectService {

    private static final Logger log = LoggerFactory.getLogger(ObjectService.class);

    private final BucketServiceClient bucketClient;
    private final StoredObjectRepository repository;
    private final FileSystemObjectStorage storage;
    private final Clock clock;

    public ObjectService(BucketServiceClient bucketClient, StoredObjectRepository repository,
            FileSystemObjectStorage storage, Clock clock) {
        this.bucketClient = bucketClient;
        this.repository = repository;
        this.storage = storage;
        this.clock = clock;
    }

    /**
     * Ordem: arquivo primeiro, metadados depois. Se o insert falhar, o arquivo é apagado.
     * A pior falha possível (queda entre os dois passos) deixa um arquivo órfão sem referência,
     * o que gera lixo, mas nunca expõe dados nem cria registro apontando para arquivo inexistente.
     */
    public StoredObject upload(Caller caller, UUID bucketId, String originalFilename, String contentType,
            InputStream content) {
        BucketInfo bucket = bucketClient.fetchBucket(bucketId, caller.bearerToken());
        if (!bucket.isActive()) {
            throw new BucketNotWritableException();
        }

        UUID objectId = UUID.randomUUID();
        StoredFile stored = storage.store(bucketId, objectId, content);
        String safeName = FilenameSanitizer.sanitize(originalFilename);
        try {
            StoredObject saved = repository.saveAndFlush(new StoredObject(objectId, bucketId, safeName,
                    stored.storageKey(), stored.sizeBytes(), normalizeContentType(contentType), stored.sha256(),
                    clock.instant()));
            log.info("op=upload result=success bucketId={} objectId={} sizeBytes={}",
                    bucketId, objectId, stored.sizeBytes());
            return saved;
        } catch (RuntimeException e) {
            rollbackFile(stored.storageKey());
            throw e;
        }
    }

    public Page<StoredObject> list(Caller caller, UUID bucketId, Pageable pageable) {
        bucketClient.fetchBucket(bucketId, caller.bearerToken());
        return repository.findByBucketId(bucketId, pageable);
    }

    public StoredObject getMetadata(Caller caller, UUID bucketId, UUID objectId) {
        bucketClient.fetchBucket(bucketId, caller.bearerToken());
        return repository.findByIdAndBucketId(objectId, bucketId).orElseThrow(ObjectNotFoundException::new);
    }

    public DownloadedObject download(Caller caller, UUID bucketId, UUID objectId) {
        StoredObject object = getMetadata(caller, bucketId, objectId);
        InputStream stream = storage.open(object.getStorageKey());
        return new DownloadedObject(object.getOriginalFilename(), object.getSizeBytes(), stream);
    }

    /**
     * Ordem: remove a linha (commit) e depois apaga o arquivo. Se apagar o arquivo falhar, sobra um
     * arquivo órfão (lixo sem referência); o contrário deixaria um registro apontando para o nada.
     */
    public void delete(Caller caller, UUID bucketId, UUID objectId) {
        StoredObject object = getMetadata(caller, bucketId, objectId);
        repository.delete(object);
        repository.flush();
        try {
            storage.delete(object.getStorageKey());
        } catch (StorageException e) {
            log.error("op=delete-file result=orphan bucketId={} objectId={} storageKey={}",
                    bucketId, objectId, object.getStorageKey(), e);
        }
        log.info("op=delete result=success bucketId={} objectId={}", bucketId, objectId);
    }

    /** Usado pelo endpoint interno (bucket-service). Sem identidade de usuário: a rota exige scope=internal. */
    public long countObjects(UUID bucketId) {
        return repository.countByBucketId(bucketId);
    }

    private void rollbackFile(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (StorageException e) {
            log.error("op=upload-rollback result=orphan storageKey={}", storageKey, e);
        }
    }

    private static String normalizeContentType(String contentType) {
        // Tratado como dado não confiável: só guardamos se tiver formato mínimo de tipo MIME.
        if (contentType == null || contentType.length() > 255 || !contentType.matches("^[\\w.+-]+/[\\w.+-]+.*$")) {
            return null;
        }
        return contentType;
    }
}
