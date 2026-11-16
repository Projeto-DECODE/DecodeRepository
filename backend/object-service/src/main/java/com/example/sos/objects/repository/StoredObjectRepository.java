package com.example.sos.objects.repository;

import com.example.sos.objects.domain.StoredObject;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredObjectRepository extends JpaRepository<StoredObject, UUID> {

    /** Único meio de buscar um objeto por ID: sempre junto do bucket (evita IDOR/BOLA entre buckets). */
    Optional<StoredObject> findByIdAndBucketId(UUID id, UUID bucketId);

    Page<StoredObject> findByBucketId(UUID bucketId, Pageable pageable);

    long countByBucketId(UUID bucketId);
}
