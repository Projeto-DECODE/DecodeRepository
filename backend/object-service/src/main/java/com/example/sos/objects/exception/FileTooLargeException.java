package com.example.sos.objects.exception;

public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException() {
        super("O arquivo excede o tamanho máximo permitido");
    }
}
