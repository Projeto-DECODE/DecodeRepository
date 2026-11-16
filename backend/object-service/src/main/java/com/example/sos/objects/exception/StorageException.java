package com.example.sos.objects.exception;

/** Falha no armazenamento físico. A mensagem é interna: nunca vai para a resposta HTTP. */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
