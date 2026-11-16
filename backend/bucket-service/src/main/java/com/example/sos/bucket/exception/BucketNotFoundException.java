package com.example.sos.bucket.exception;

/** Usada tanto para bucket inexistente quanto para bucket de outro usuário (evita enumeração). */
public class BucketNotFoundException extends RuntimeException {

    public BucketNotFoundException() {
        super("Bucket não encontrado");
    }
}
