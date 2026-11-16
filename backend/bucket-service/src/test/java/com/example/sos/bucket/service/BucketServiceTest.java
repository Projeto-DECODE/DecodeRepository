package com.example.sos.bucket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sos.bucket.client.ObjectServiceClient;
import com.example.sos.bucket.domain.Bucket;
import com.example.sos.bucket.domain.BucketStatus;
import com.example.sos.bucket.exception.BucketNameAlreadyExistsException;
import com.example.sos.bucket.exception.BucketNotEmptyException;
import com.example.sos.bucket.exception.BucketNotFoundException;
import com.example.sos.bucket.exception.DependencyUnavailableException;
import com.example.sos.bucket.repository.BucketRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BucketServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final UUID owner = UUID.randomUUID();
    private final UUID bucketId = UUID.randomUUID();

    @Mock BucketRepository repository;
    @Mock ObjectServiceClient objectClient;

    private BucketService service() {
        return new BucketService(repository, objectClient, clock);
    }

    private void bucketExistsForOwner() {
        when(repository.findByIdAndOwnerUserId(bucketId, owner))
                .thenReturn(Optional.of(new Bucket("docs", owner, clock.instant())));
    }

    @Test
    void createRejectsDuplicateNameForSameOwner() {
        when(repository.existsByOwnerUserIdAndName(owner, "docs")).thenReturn(true);

        assertThrows(BucketNameAlreadyExistsException.class, () -> service().create(owner, "docs"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createStoresTheOwnerTakenFromTheCaller() {
        when(repository.existsByOwnerUserIdAndName(owner, "docs")).thenReturn(false);
        when(repository.saveAndFlush(any(Bucket.class))).thenAnswer(inv -> inv.getArgument(0));

        Bucket created = service().create(owner, "docs");

        assertEquals(owner, created.getOwnerUserId());
        assertEquals(BucketStatus.ACTIVE, created.getStatus());
    }

    @Test
    void getFromAnotherUserLooksLikeNotFound() {
        when(repository.findByIdAndOwnerUserId(bucketId, owner)).thenReturn(Optional.empty());

        assertThrows(BucketNotFoundException.class, () -> service().get(owner, bucketId));
    }

    @Test
    void deleteEmptyBucketMarksDeletingThenRemoves() {
        bucketExistsForOwner();
        when(objectClient.countObjects(bucketId)).thenReturn(0L);
        when(repository.deleteByIdAndStatus(bucketId, BucketStatus.DELETING)).thenReturn(1);

        service().delete(owner, bucketId);

        InOrder order = inOrder(repository, objectClient);
        order.verify(repository).transition(bucketId, BucketStatus.ACTIVE, BucketStatus.DELETING);
        order.verify(objectClient).countObjects(bucketId);
        order.verify(repository).deleteByIdAndStatus(bucketId, BucketStatus.DELETING);
    }

    @Test
    void deleteNonEmptyBucketRestoresActiveAndConflicts() {
        bucketExistsForOwner();
        when(objectClient.countObjects(bucketId)).thenReturn(3L);

        assertThrows(BucketNotEmptyException.class, () -> service().delete(owner, bucketId));

        verify(repository).transition(bucketId, BucketStatus.DELETING, BucketStatus.ACTIVE);
        verify(repository, never()).deleteByIdAndStatus(any(), any());
    }

    @Test
    void deleteFailsClosedWhenObjectServiceIsDown() {
        bucketExistsForOwner();
        when(objectClient.countObjects(bucketId)).thenThrow(new DependencyUnavailableException("down"));

        assertThrows(DependencyUnavailableException.class, () -> service().delete(owner, bucketId));

        verify(repository).transition(bucketId, BucketStatus.DELETING, BucketStatus.ACTIVE);
        verify(repository, never()).deleteByIdAndStatus(any(), any());
    }

    @Test
    void deleteOfAnotherUsersBucketNeverTouchesObjectService() {
        when(repository.findByIdAndOwnerUserId(bucketId, owner)).thenReturn(Optional.empty());

        assertThrows(BucketNotFoundException.class, () -> service().delete(owner, bucketId));

        verify(objectClient, never()).countObjects(any());
        verify(repository, never()).transition(any(), any(), any());
    }
}
