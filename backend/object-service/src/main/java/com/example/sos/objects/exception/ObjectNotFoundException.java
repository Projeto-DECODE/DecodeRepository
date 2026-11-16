package com.example.sos.objects.exception;

public class ObjectNotFoundException extends RuntimeException {

    public ObjectNotFoundException() {
        super("Objeto não encontrado");
    }
}
