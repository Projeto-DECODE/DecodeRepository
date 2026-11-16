package com.example.sos.bucket.exception;

public class InvalidTokenSubjectException extends RuntimeException {

    public InvalidTokenSubjectException() {
        super("Token sem identificador de usuário válido");
    }
}
