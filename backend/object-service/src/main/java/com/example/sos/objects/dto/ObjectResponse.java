package com.example.sos.objects.dto;

import com.example.sos.objects.domain.StoredObject;
import java.time.Instant;
import java.util.UUID;

/** Metadados públicos. Nunca expõe storageKey nem caminho físico. */
public record ObjectResponse(
        UUID id,
        UUID bucketId,
        String originalFilename,
        long sizeBytes,
        String contentType,
        String sha256,
        Instant createdAt) {

    public static ObjectResponse from(StoredObject o) {
        return new ObjectResponse(o.getId(), o.getBucketId(), o.getOriginalFilename(), o.getSizeBytes(),
                o.getContentType(), o.getSha256(), o.getCreatedAt());
    }
}
