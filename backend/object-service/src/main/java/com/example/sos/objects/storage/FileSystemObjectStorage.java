package com.example.sos.objects.storage;

import com.example.sos.objects.exception.EmptyFileException;
import com.example.sos.objects.exception.FileTooLargeException;
import com.example.sos.objects.exception.StorageException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Armazenamento em sistema de arquivos. A chave física é montada somente a partir de UUIDs gerados
 * no servidor ({@code {bucketId}/{objectId[0..2]}/{objectId}}); o nome enviado pelo cliente nunca
 * participa do caminho. Mesmo assim, todo caminho resolvido é normalizado e conferido contra a raiz
 * (defesa em profundidade).
 *
 * <p>Upload em streaming: os bytes vão para um arquivo temporário (calculando SHA-256 e impondo o
 * limite de tamanho) e só depois são movidos para o destino final, de forma atômica quando o sistema
 * de arquivos permite. Nada é carregado inteiro na memória.
 */
public class FileSystemObjectStorage {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final String TMP_DIR = ".tmp";

    private final Path root;
    private final long maxFileSizeBytes;

    public FileSystemObjectStorage(Path root, long maxFileSizeBytes) {
        this.root = root.toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
        try {
            Files.createDirectories(this.root.resolve(TMP_DIR));
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível preparar o diretório de armazenamento", e);
        }
    }

    public StoredFile store(UUID bucketId, UUID objectId, InputStream content) {
        String storageKey = bucketId + "/" + objectId.toString().substring(0, 2) + "/" + objectId;
        Path target = resolveSafely(storageKey);
        Path temp = null;
        try {
            temp = Files.createTempFile(root.resolve(TMP_DIR), "upload-", ".part");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = copyLimited(new DigestInputStream(content, digest), temp);

            if (size == 0) {
                throw new EmptyFileException();
            }

            Files.createDirectories(target.getParent());
            // Nunca sobrescreve: se o destino já existir (colisão impossível com UUID, mas verificada),
            // a gravação falha em vez de substituir dados.
            if (Files.exists(target)) {
                throw new StorageException("Destino já existe para a chave gerada");
            }
            moveAtomically(temp, target);
            temp = null;
            return new StoredFile(storageKey, size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException e) {
            throw new StorageException("Falha ao gravar o arquivo", e);
        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 indisponível", e);
        } finally {
            deleteQuietly(temp);
        }
    }

    public InputStream open(String storageKey) {
        Path file = resolveSafely(storageKey);
        try {
            return Files.newInputStream(file, StandardOpenOption.READ);
        } catch (NoSuchFileException e) {
            throw new StorageException("Arquivo ausente no armazenamento para a chave " + storageKey, e);
        } catch (IOException e) {
            throw new StorageException("Falha ao abrir o arquivo", e);
        }
    }

    /** Remove o arquivo. Retorna false se ele já não existia. */
    public boolean delete(String storageKey) {
        Path file = resolveSafely(storageKey);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new StorageException("Falha ao excluir o arquivo", e);
        }
    }

    public boolean exists(String storageKey) {
        return Files.exists(resolveSafely(storageKey));
    }

    /**
     * Resolve a chave sob a raiz e garante que o resultado continua dentro dela.
     * Rejeita qualquer tentativa de escape ("..", caminho absoluto, etc.).
     */
    Path resolveSafely(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.indexOf('\0') >= 0) {
            throw new StorageException("Chave de armazenamento inválida");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new StorageException("Chave de armazenamento fora do diretório permitido");
        }
        return resolved;
    }

    private long copyLimited(InputStream in, Path temp) throws IOException {
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (var out = Files.newOutputStream(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxFileSizeBytes) {
                    throw new FileTooLargeException();
                }
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // arquivo temporário órfão em .tmp: não expõe dados e não afeta a operação
        }
    }
}
