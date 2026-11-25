DROP INDEX IF EXISTS idx_accounts_email;
DROP INDEX IF EXISTS idx_idempotency_key;
DROP INDEX IF EXISTS idx_ledger_account_id;
DROP INDEX IF EXISTS idx_ledger_created_at;

CREATE INDEX idx_ledger_account_created ON ledger_entries (account_id, created_at DESC);

ALTER TABLE accounts
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE ledger_entries
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE transfer_records
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE idempotency_records
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN locked_until TYPE TIMESTAMPTZ USING locked_until AT TIME ZONE 'UTC';

ALTER TABLE reconciliation_logs
    ALTER COLUMN reconciled_at TYPE TIMESTAMPTZ USING reconciled_at AT TIME ZONE 'UTC';
