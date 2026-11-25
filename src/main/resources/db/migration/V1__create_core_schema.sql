-- ============================================
-- V1: Core Schema for Double-Entry Ledger
-- Distributed Transactional Wallet Service
-- ============================================

-- ============================================
-- 1. Accounts Table (Operational Truth)
-- ============================================
CREATE TABLE accounts (
    id                  BIGSERIAL       PRIMARY KEY,
    account_holder_name VARCHAR(255)    NOT NULL,
    email               VARCHAR(255)    NOT NULL UNIQUE,
    available_balance   NUMERIC(19, 4)  NOT NULL DEFAULT 0.0000,
    locked_balance      NUMERIC(19, 4)  NOT NULL DEFAULT 0.0000,
    currency            VARCHAR(3)      NOT NULL DEFAULT 'INR',
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Database-level invariant: balance can NEVER go negative
    CONSTRAINT chk_available_balance_non_negative CHECK (available_balance >= 0),
    CONSTRAINT chk_locked_balance_non_negative CHECK (locked_balance >= 0),
    CONSTRAINT chk_status_valid CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

-- Index for quick lookups
CREATE INDEX idx_accounts_email ON accounts (email);
CREATE INDEX idx_accounts_status ON accounts (status);

-- ============================================
-- 2. Ledger Entries Table (Audit Truth)
-- Immutable, append-only journal
-- ============================================
CREATE TABLE ledger_entries (
    id                  BIGSERIAL       PRIMARY KEY,
    transaction_id      UUID            NOT NULL,
    account_id          BIGINT          NOT NULL REFERENCES accounts(id),
    entry_type          VARCHAR(10)     NOT NULL,
    amount              NUMERIC(19, 4)  NOT NULL,
    balance_after       NUMERIC(19, 4)  NOT NULL,
    description         VARCHAR(500),
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Enforce only DEBIT or CREDIT
    CONSTRAINT chk_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    -- Amount must always be positive (direction encoded in entry_type)
    CONSTRAINT chk_amount_positive CHECK (amount > 0)
);

-- Indexes for ledger queries
CREATE INDEX idx_ledger_transaction_id ON ledger_entries (transaction_id);
CREATE INDEX idx_ledger_account_id ON ledger_entries (account_id);
CREATE INDEX idx_ledger_created_at ON ledger_entries (created_at);

-- ============================================
-- 3. Transfer Records Table
-- Links debit and credit legs of a transfer
-- ============================================
CREATE TABLE transfer_records (
    id                      BIGSERIAL       PRIMARY KEY,
    transaction_id          UUID            NOT NULL UNIQUE,
    source_account_id       BIGINT          NOT NULL REFERENCES accounts(id),
    destination_account_id  BIGINT          NOT NULL REFERENCES accounts(id),
    amount                  NUMERIC(19, 4)  NOT NULL,
    currency                VARCHAR(3)      NOT NULL DEFAULT 'INR',
    status                  VARCHAR(20)     NOT NULL DEFAULT 'COMPLETED',
    description             VARCHAR(500),
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transfer_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_different_accounts CHECK (source_account_id != destination_account_id),
    CONSTRAINT chk_transfer_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED'))
);

CREATE INDEX idx_transfer_source ON transfer_records (source_account_id);
CREATE INDEX idx_transfer_destination ON transfer_records (destination_account_id);
CREATE INDEX idx_transfer_created_at ON transfer_records (created_at);

-- ============================================
-- 4. Idempotency Records Table
-- Prevents duplicate processing of retried requests
-- ============================================
CREATE TABLE idempotency_records (
    id                  BIGSERIAL       PRIMARY KEY,
    idempotency_key     VARCHAR(255)    NOT NULL UNIQUE,
    request_hash        VARCHAR(64)     NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'IN_PROGRESS',
    response_status     INTEGER,
    response_body       TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_idempotency_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_idempotency_key ON idempotency_records (idempotency_key);

-- ============================================
-- 5. Reconciliation Log Table
-- Tracks balance reconciliation runs
-- ============================================
CREATE TABLE reconciliation_logs (
    id                  BIGSERIAL       PRIMARY KEY,
    account_id          BIGINT          NOT NULL REFERENCES accounts(id),
    snapshot_balance    NUMERIC(19, 4)  NOT NULL,
    computed_balance    NUMERIC(19, 4)  NOT NULL,
    is_matched          BOOLEAN         NOT NULL,
    discrepancy         NUMERIC(19, 4)  NOT NULL DEFAULT 0.0000,
    reconciled_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reconciliation_account ON reconciliation_logs (account_id);
CREATE INDEX idx_reconciliation_matched ON reconciliation_logs (is_matched);
