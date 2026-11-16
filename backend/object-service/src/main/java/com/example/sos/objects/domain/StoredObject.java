package com.example.sos.objects.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Metadados de um objeto. Os bytes ficam no sistema de arquivos, em {@code storageKey}.
 * O ID é definido pela aplicação (precisa existir antes de gravar o arquivo), por isso a entidade
 * implementa Persistable: o Spring Data usa persist em vez de um merge com SELECT.
 */
@Entity
@Table(name = "stored_objects")
public class StoredObject implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "bucket_id", nullable = false, updatable = false)
    private UUID bucketId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 255)
    private String storageKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected StoredObject() {
        // exigido pelo JPA
    }

    public StoredObject(UUID id, UUID bucketId, String originalFilename, String storageKey, long sizeBytes,
            String contentType, String sha256, Instant createdAt) {
        this.id = id;
        this.bucketId = bucketId;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.sizeBytes = sizeBytes;
        this.contentType = contentType;
        this.sha256 = sha256;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public UUID getBucketId() {
        return bucketId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getContentType() {
        return contentType;
    }

    public String getSha256() {
        return sha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
