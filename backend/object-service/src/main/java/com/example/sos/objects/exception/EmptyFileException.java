package com.example.sos.objects.exception;

public class EmptyFileException extends RuntimeException {

    public EmptyFileException() {
        super("O arquivo enviado está vazio");
    }
}
