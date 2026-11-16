package com.example.sos.objects.exception;

public class InvalidTokenSubjectException extends RuntimeException {

    public InvalidTokenSubjectException() {
        super("Token sem identificador de usuário válido");
    }
}
