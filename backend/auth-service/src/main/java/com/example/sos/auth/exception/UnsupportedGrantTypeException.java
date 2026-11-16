package com.example.sos.auth.exception;

public class UnsupportedGrantTypeException extends RuntimeException {

    public UnsupportedGrantTypeException() {
        super("grant_type não suportado");
    }
}
