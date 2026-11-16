CREATE TABLE stored_objects (
    id                UUID         PRIMARY KEY,
    bucket_id         UUID         NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_key       VARCHAR(255) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    content_type      VARCHAR(255),
    sha256            VARCHAR(64)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_stored_objects_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_stored_objects_size CHECK (size_bytes > 0)
);

-- Listagem paginada por bucket (mais recentes primeiro) e contagem por bucket.
CREATE INDEX idx_stored_objects_bucket_created ON stored_objects (bucket_id, created_at DESC);
