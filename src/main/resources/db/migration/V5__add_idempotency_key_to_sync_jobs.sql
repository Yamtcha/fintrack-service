ALTER TABLE IF EXISTS sync_jobs
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);

-- Make idempotency_key unique globally
CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_jobs_idempotency_key_unique ON sync_jobs(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_sync_jobs_idempotency_key ON sync_jobs(idempotency_key);
