package com.example.sos.objects.storage;

/** Resultado de uma gravação: chave de armazenamento (caminho relativo), tamanho e SHA-256 em hex. */
public record StoredFile(String storageKey, long sizeBytes, String sha256) {
}
