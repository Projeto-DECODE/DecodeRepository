package com.example.sos.bucket.dto;

import com.example.sos.bucket.domain.Bucket;
import com.example.sos.bucket.domain.BucketStatus;
import java.time.Instant;
import java.util.UUID;

public record BucketResponse(UUID id, String name, BucketStatus status, Instant createdAt) {

    public static BucketResponse from(Bucket bucket) {
        return new BucketResponse(bucket.getId(), bucket.getName(), bucket.getStatus(), bucket.getCreatedAt());
    }
}
