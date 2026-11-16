package com.example.sos.bucket.exception;

public class BucketNotEmptyException extends RuntimeException {

    public BucketNotEmptyException() {
        super("O bucket ainda contém objetos. Exclua os objetos antes de excluir o bucket");
    }
}
