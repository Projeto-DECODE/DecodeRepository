package com.example.sos.objects.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.sos.objects.client.BucketInfo;
import com.example.sos.objects.client.BucketServiceClient;
import com.example.sos.objects.domain.StoredObject;
import com.example.sos.objects.exception.BucketNotFoundException;
import com.example.sos.objects.exception.BucketNotWritableException;
import com.example.sos.objects.exception.DependencyUnavailableException;
import com.example.sos.objects.exception.ObjectNotFoundException;
import com.example.sos.objects.exception.StorageException;
import com.example.sos.objects.repository.StoredObjectRepository;
import com.example.sos.objects.security.Caller;
import com.example.sos.objects.storage.FileSystemObjectStorage;
import com.example.sos.objects.storage.StoredFile;
import java.io.ByteArrayInputStream;
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
class ObjectServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final UUID bucketId = UUID.randomUUID();
    private final Caller caller = new Caller(UUID.randomUUID(), "user-token");

    @Mock BucketServiceClient bucketClient;
    @Mock StoredObjectRepository repository;
    @Mock FileSystemObjectStorage storage;

    private ObjectService service() {
        return new ObjectService(bucketClient, repository, storage, clock);
    }

    private void activeBucket() {
        when(bucketClient.fetchBucket(bucketId, "user-token"))
                .thenReturn(new BucketInfo(bucketId, "docs", "ACTIVE"));
    }

    private StoredObject sampleObject(UUID id) {
        return new StoredObject(id, bucketId, "a.txt", bucketId + "/ab/" + id, 5, "text/plain", "h", clock.instant());
    }

    // ---------- upload ----------

    @Test
    void uploadForwardsTheUserTokenAndStoresFileBeforeMetadata() {
        activeBucket();
        when(storage.store(any(), any(), any())).thenReturn(new StoredFile("k/ab/x", 5, "hash"));
        when(repository.saveAndFlush(any(StoredObject.class))).thenAnswer(i -> i.getArgument(0));

        StoredObject saved = service().upload(caller, bucketId, "../../evil.txt", "text/plain",
                new ByteArrayInputStream(new byte[] {1}));

        assertEquals("evil.txt", saved.getOriginalFilename(), "nome original é sanitizado e só metadado");
        assertEquals("k/ab/x", saved.getStorageKey());
        InOrder order = inOrder(bucketClient, storage, repository);
        order.verify(bucketClient).fetchBucket(bucketId, "user-token");
        order.verify(storage).store(any(), any(), any());
        order.verify(repository).saveAndFlush(any(StoredObject.class));
    }

    @Test
    void uploadToBucketBeingDeletedIsRejectedBeforeTouchingDisk() {
        when(bucketClient.fetchBucket(bucketId, "user-token"))
                .thenReturn(new BucketInfo(bucketId, "docs", "DELETING"));

        assertThrows(BucketNotWritableException.class,
                () -> service().upload(caller, bucketId, "a.txt", null, new ByteArrayInputStream(new byte[] {1})));

        verifyNoInteractions(storage, repository);
    }

    @Test
    void uploadToAnotherUsersBucketNeverTouchesDisk() {
        when(bucketClient.fetchBucket(bucketId, "user-token")).thenThrow(new BucketNotFoundException());

        assertThrows(BucketNotFoundException.class,
                () -> service().upload(caller, bucketId, "a.txt", null, new ByteArrayInputStream(new byte[] {1})));

        verifyNoInteractions(storage, repository);
    }

    @Test
    void uploadFailsClosedWhenBucketServiceIsDown() {
        when(bucketClient.fetchBucket(bucketId, "user-token")).thenThrow(new DependencyUnavailableException("down"));

        assertThrows(DependencyUnavailableException.class,
                () -> service().upload(caller, bucketId, "a.txt", null, new ByteArrayInputStream(new byte[] {1})));

        verifyNoInteractions(storage, repository);
    }

    @Test
    void uploadRemovesTheFileWhenTheDatabaseInsertFails() {
        activeBucket();
        when(storage.store(any(), any(), any())).thenReturn(new StoredFile("k/ab/x", 5, "hash"));
        when(repository.saveAndFlush(any(StoredObject.class))).thenThrow(new RuntimeException("db fora do ar"));

        assertThrows(RuntimeException.class,
                () -> service().upload(caller, bucketId, "a.txt", null, new ByteArrayInputStream(new byte[] {1})));

        verify(storage).delete("k/ab/x");
    }

    @Test
    void untrustedContentTypeIsDroppedWhenMalformed() {
        activeBucket();
        when(storage.store(any(), any(), any())).thenReturn(new StoredFile("k/ab/x", 5, "hash"));
        when(repository.saveAndFlush(any(StoredObject.class))).thenAnswer(i -> i.getArgument(0));

        StoredObject saved = service().upload(caller, bucketId, "a.bin", "<script>alert(1)</script>",
                new ByteArrayInputStream(new byte[] {1}));

        assertNull(saved.getContentType());
    }

    // ---------- leitura e propriedade do objeto ----------

    @Test
    void objectIsAlwaysLookedUpTogetherWithItsBucket() {
        activeBucket();
        UUID objectId = UUID.randomUUID();
        when(repository.findByIdAndBucketId(objectId, bucketId)).thenReturn(Optional.empty());

        assertThrows(ObjectNotFoundException.class, () -> service().getMetadata(caller, bucketId, objectId));

        verify(repository, never()).findById(any());
    }

    @Test
    void downloadOfAnotherUsersBucketDoesNotOpenAnyFile() {
        when(bucketClient.fetchBucket(bucketId, "user-token")).thenThrow(new BucketNotFoundException());

        assertThrows(BucketNotFoundException.class, () -> service().download(caller, bucketId, UUID.randomUUID()));

        verifyNoInteractions(storage, repository);
    }

    // ---------- exclusão ----------

    @Test
    void deleteRemovesRowThenFile() {
        activeBucket();
        UUID objectId = UUID.randomUUID();
        StoredObject object = sampleObject(objectId);
        when(repository.findByIdAndBucketId(objectId, bucketId)).thenReturn(Optional.of(object));

        service().delete(caller, bucketId, objectId);

        InOrder order = inOrder(repository, storage);
        order.verify(repository).delete(object);
        order.verify(repository).flush();
        order.verify(storage).delete(object.getStorageKey());
    }

    @Test
    void deleteSucceedsEvenIfThePhysicalFileCannotBeRemoved() {
        activeBucket();
        UUID objectId = UUID.randomUUID();
        StoredObject object = sampleObject(objectId);
        when(repository.findByIdAndBucketId(objectId, bucketId)).thenReturn(Optional.of(object));
        doThrow(new StorageException("disco cheio")).when(storage).delete(anyString());

        service().delete(caller, bucketId, objectId); // não lança: o arquivo órfão é logado

        verify(repository).delete(object);
    }

    @Test
    void deleteOfObjectFromAnotherBucketIsNotFoundAndKeepsTheFile() {
        activeBucket();
        UUID objectId = UUID.randomUUID();
        when(repository.findByIdAndBucketId(objectId, bucketId)).thenReturn(Optional.empty());

        assertThrows(ObjectNotFoundException.class, () -> service().delete(caller, bucketId, objectId));

        verify(repository, never()).delete(any(StoredObject.class));
        verify(storage, never()).delete(anyString());
    }
}
