package com.example.sos.objects.exception;

/** Outro serviço não respondeu de forma confiável. A operação falha fechada: nunca libera o acesso. */
public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public DependencyUnavailableException(String message) {
        super(message);
    }
}
