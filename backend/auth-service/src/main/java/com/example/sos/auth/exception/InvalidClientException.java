package com.example.sos.auth.exception;

public class InvalidClientException extends RuntimeException {

    public InvalidClientException() {
        super("Cliente de serviço inválido");
    }
}
