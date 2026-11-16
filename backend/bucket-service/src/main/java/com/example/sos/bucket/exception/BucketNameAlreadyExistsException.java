package com.example.sos.bucket.exception;

public class BucketNameAlreadyExistsException extends RuntimeException {

    public BucketNameAlreadyExistsException() {
        super("Você já possui um bucket com esse nome");
    }
}
