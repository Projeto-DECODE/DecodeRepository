package com.example.sos.bucket.domain;

public enum BucketStatus {
    /** Aceita uploads e operações normais. */
    ACTIVE,
    /** Exclusão em andamento: o object-service rejeita novos uploads (409). */
    DELETING
}
