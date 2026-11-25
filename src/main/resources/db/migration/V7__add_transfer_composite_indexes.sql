DROP INDEX IF EXISTS idx_transfer_source;
DROP INDEX IF EXISTS idx_transfer_destination;

CREATE INDEX idx_transfer_source_created ON transfer_records (source_account_id, created_at DESC);
CREATE INDEX idx_transfer_dest_created ON transfer_records (destination_account_id, created_at DESC);
