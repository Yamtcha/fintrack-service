CREATE TABLE sync_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id            VARCHAR(255) NOT NULL,
    source_id           UUID         NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    status              VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    total_received      INTEGER      NOT NULL DEFAULT 0,
    total_processed     INTEGER      NOT NULL DEFAULT 0,
    total_failed        INTEGER      NOT NULL DEFAULT 0,
    failure_reason      TEXT,
    started_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX idx_sync_jobs_batch_source ON sync_jobs(batch_id, source_id);
CREATE INDEX idx_sync_jobs_source_id ON sync_jobs(source_id);
CREATE INDEX idx_sync_jobs_status ON sync_jobs(status);
