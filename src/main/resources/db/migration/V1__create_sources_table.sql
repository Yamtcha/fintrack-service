CREATE TABLE sources (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    source_type     VARCHAR(50)  NOT NULL,
    config          JSONB,
    status          VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    registered_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sources_source_type ON sources(source_type);
