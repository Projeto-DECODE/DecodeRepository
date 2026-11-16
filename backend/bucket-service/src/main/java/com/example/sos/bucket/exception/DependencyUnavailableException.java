package com.example.sos.bucket.exception;

/**
 * Outro serviço não respondeu de forma confiável. A operação falha fechada (nunca presume "vazio").
 */
public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public DependencyUnavailableException(String message) {
        super(message);
    }
}
