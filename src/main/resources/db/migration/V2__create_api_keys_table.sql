CREATE TABLE api_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_hash        VARCHAR(64)  NOT NULL,
    prefix          VARCHAR(16)  NOT NULL,
    source_id       UUID         NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    scope           VARCHAR(50)  NOT NULL DEFAULT 'READ_WRITE',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE,
    last_used_at    TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX idx_api_keys_prefix ON api_keys(prefix) WHERE active = TRUE;
CREATE INDEX idx_api_keys_source_id ON api_keys(source_id);
