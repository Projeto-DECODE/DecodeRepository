CREATE TABLE buckets (
    id            UUID        PRIMARY KEY,
    name          VARCHAR(63) NOT NULL,
    owner_user_id UUID        NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A unicidade é por proprietário; o índice também atende a listagem por owner_user_id.
    CONSTRAINT uq_buckets_owner_name UNIQUE (owner_user_id, name),
    CONSTRAINT ck_buckets_status CHECK (status IN ('ACTIVE', 'DELETING'))
);
