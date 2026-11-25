ALTER TABLE idempotency_records
    ADD COLUMN locked_until TIMESTAMP;

CREATE INDEX idx_idempotency_status_locked_until
    ON idempotency_records (status, locked_until)
    WHERE status = 'IN_PROGRESS';
