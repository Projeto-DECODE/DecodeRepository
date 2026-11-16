package com.example.sos.bucket.repository;

import com.example.sos.bucket.domain.Bucket;
import com.example.sos.bucket.domain.BucketStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BucketRepository extends JpaRepository<Bucket, UUID> {

    /** Único meio de buscar um bucket por ID: sempre junto do proprietário (evita IDOR/BOLA). */
    Optional<Bucket> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    List<Bucket> findByOwnerUserIdOrderByCreatedAtAsc(UUID ownerUserId);

    boolean existsByOwnerUserIdAndName(UUID ownerUserId, String name);

    /** Transição atômica de status; retorna 0 se o bucket não estava no estado esperado. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Bucket b set b.status = :newStatus where b.id = :id and b.status = :expectedStatus")
    int transition(@Param("id") UUID id, @Param("expectedStatus") BucketStatus expectedStatus,
            @Param("newStatus") BucketStatus newStatus);

    /** Só exclui se o bucket estiver em DELETING, ou seja, depois de bloquear novos uploads. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Bucket b where b.id = :id and b.status = :requiredStatus")
    int deleteByIdAndStatus(@Param("id") UUID id, @Param("requiredStatus") BucketStatus requiredStatus);
}
