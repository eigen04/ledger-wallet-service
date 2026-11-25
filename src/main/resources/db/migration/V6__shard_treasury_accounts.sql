ALTER TABLE accounts
    ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE accounts
    ADD CONSTRAINT chk_account_type CHECK (account_type IN ('USER', 'TREASURY'));

UPDATE accounts
SET account_type = 'TREASURY'
WHERE email = 'system.treasury.inr@ledger.internal';

ALTER TABLE accounts
    DROP CONSTRAINT chk_available_balance_non_negative;

ALTER TABLE accounts
    ADD CONSTRAINT chk_available_balance_non_negative
        CHECK (account_type = 'TREASURY' OR available_balance >= 0);

INSERT INTO accounts (account_holder_name, email, available_balance, currency, status, account_type)
SELECT
    'SYSTEM_TREASURY_' || ccy || '_' || shard,
    'system.treasury.' || LOWER(ccy) || '.' || shard || '@ledger.internal',
    0.0000,
    ccy,
    'ACTIVE',
    'TREASURY'
FROM unnest(ARRAY['INR', 'USD', 'EUR', 'GBP']) AS ccy
CROSS JOIN generate_series(0, 7) AS shard
ON CONFLICT (email) DO NOTHING;
