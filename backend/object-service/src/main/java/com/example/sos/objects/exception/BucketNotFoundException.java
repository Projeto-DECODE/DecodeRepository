package com.example.sos.objects.exception;

/** Bucket inexistente ou pertencente a outro usuário (o bucket-service não distingue os dois casos). */
public class BucketNotFoundException extends RuntimeException {

    public BucketNotFoundException() {
        super("Bucket não encontrado");
    }
}
