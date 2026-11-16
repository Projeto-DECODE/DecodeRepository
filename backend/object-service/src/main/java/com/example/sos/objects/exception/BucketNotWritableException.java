package com.example.sos.objects.exception;

/** O bucket está em exclusão (DELETING) e não aceita novos uploads. */
public class BucketNotWritableException extends RuntimeException {

    public BucketNotWritableException() {
        super("O bucket está sendo excluído e não aceita novos objetos");
    }
}
