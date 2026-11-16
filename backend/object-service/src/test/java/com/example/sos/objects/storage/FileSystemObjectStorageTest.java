package com.example.sos.objects.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sos.objects.exception.EmptyFileException;
import com.example.sos.objects.exception.FileTooLargeException;
import com.example.sos.objects.exception.StorageException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileSystemObjectStorageTest {

    @TempDir
    Path root;

    private FileSystemObjectStorage storage(long max) {
        return new FileSystemObjectStorage(root, max);
    }

    private static ByteArrayInputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void storesFileAndReturnsSizeAndSha256() throws Exception {
        UUID bucket = UUID.randomUUID();
        UUID object = UUID.randomUUID();

        StoredFile stored = storage(1024).store(bucket, object, bytes("conteudo de teste"));

        assertEquals(17, stored.sizeBytes());
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("conteudo de teste".getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, stored.sha256());
        assertEquals(bucket + "/" + object.toString().substring(0, 2) + "/" + object, stored.storageKey());
        assertTrue(Files.exists(root.resolve(stored.storageKey())));
    }

    @Test
    void storedBytesCanBeReadBack() throws Exception {
        FileSystemObjectStorage storage = storage(1024);
        StoredFile stored = storage.store(UUID.randomUUID(), UUID.randomUUID(), bytes("ida e volta"));

        try (var in = storage.open(stored.storageKey())) {
            assertArrayEquals("ida e volta".getBytes(StandardCharsets.UTF_8), in.readAllBytes());
        }
    }

    @Test
    void rejectsFileAboveLimitAndLeavesNothingBehind() throws Exception {
        FileSystemObjectStorage storage = storage(10);
        UUID bucket = UUID.randomUUID();

        assertThrows(FileTooLargeException.class,
                () -> storage.store(bucket, UUID.randomUUID(), bytes("12345678901")));

        assertFalse(Files.exists(root.resolve(bucket.toString())), "nenhum destino deve ser criado");
        try (Stream<Path> tmp = Files.list(root.resolve(".tmp"))) {
            assertEquals(0, tmp.count(), "arquivo temporário deve ser removido");
        }
    }

    @Test
    void acceptsFileExactlyAtLimit() {
        assertEquals(10, storage(10).store(UUID.randomUUID(), UUID.randomUUID(), bytes("1234567890")).sizeBytes());
    }

    @Test
    void rejectsEmptyFile() {
        assertThrows(EmptyFileException.class,
                () -> storage(10).store(UUID.randomUUID(), UUID.randomUUID(), bytes("")));
    }

    @Test
    void deleteRemovesTheFileAndIsIdempotent() {
        FileSystemObjectStorage storage = storage(1024);
        StoredFile stored = storage.store(UUID.randomUUID(), UUID.randomUUID(), bytes("x"));

        assertTrue(storage.delete(stored.storageKey()));
        assertFalse(storage.exists(stored.storageKey()));
        assertFalse(storage.delete(stored.storageKey()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../fora.txt",
            "a/../../fora.txt",
            "../../etc/passwd",
            "/etc/passwd",
            "a/b/../../../fora",
            ".",
            "",
            " "
    })
    void pathTraversalAttemptsAreRejected(String maliciousKey) {
        FileSystemObjectStorage storage = storage(1024);

        assertThrows(StorageException.class, () -> storage.open(maliciousKey));
        assertThrows(StorageException.class, () -> storage.delete(maliciousKey));
    }

    @Test
    void nullByteInKeyIsRejected() {
        assertThrows(StorageException.class, () -> storage(1024).open("a\0b"));
    }

    @Test
    void openingAMissingFileFailsWithStorageException() {
        assertThrows(StorageException.class,
                () -> storage(1024).open(UUID.randomUUID() + "/ab/" + UUID.randomUUID()));
    }

    @Test
    void filesSurviveANewStorageInstanceOverTheSameRoot() throws IOException {
        StoredFile stored = storage(1024).store(UUID.randomUUID(), UUID.randomUUID(), bytes("persistente"));

        // Simula a reinicialização do serviço: nova instância, mesmo diretório (volume).
        try (var in = storage(1024).open(stored.storageKey())) {
            assertArrayEquals("persistente".getBytes(StandardCharsets.UTF_8), in.readAllBytes());
        }
    }

    @Test
    void doesNotOverwriteAnExistingDestination() throws IOException {
        FileSystemObjectStorage storage = storage(1024);
        UUID bucket = UUID.randomUUID();
        UUID object = UUID.randomUUID();
        StoredFile first = storage.store(bucket, object, bytes("original"));

        assertThrows(StorageException.class, () -> storage.store(bucket, object, bytes("sobrescrita")));

        try (var in = storage.open(first.storageKey())) {
            assertEquals("original", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void storageFailureWhenRootIsNotWritable() throws IOException {
        FileSystemObjectStorage storage = storage(1024);
        UUID bucket = UUID.randomUUID();
        // Um arquivo comum no lugar do diretório do bucket faz createDirectories falhar.
        Files.writeString(root.resolve(bucket.toString()), "bloqueio");

        assertThrows(StorageException.class, () -> storage.store(bucket, UUID.randomUUID(), bytes("x")));
    }
}
