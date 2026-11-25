-- ============================================
-- V2: Seed data for development & testing
-- ============================================

-- Create test accounts with initial balances
INSERT INTO accounts (account_holder_name, email, available_balance, currency, status)
VALUES
    ('Alice Sharma', 'alice@example.com', 10000.0000, 'INR', 'ACTIVE'),
    ('Bob Patel', 'bob@example.com', 5000.0000, 'INR', 'ACTIVE'),
    ('Charlie Kumar', 'charlie@example.com', 25000.0000, 'INR', 'ACTIVE'),
    ('Diana Singh', 'diana@example.com', 15000.0000, 'INR', 'ACTIVE'),
    ('Eve Reddy', 'eve@example.com', 8000.0000, 'INR', 'ACTIVE');

-- Create initial deposit ledger entries (credits) for seeded accounts
INSERT INTO ledger_entries (transaction_id, account_id, entry_type, amount, balance_after, description)
VALUES
    (gen_random_uuid(), 1, 'CREDIT', 10000.0000, 10000.0000, 'Initial deposit'),
    (gen_random_uuid(), 2, 'CREDIT', 5000.0000, 5000.0000, 'Initial deposit'),
    (gen_random_uuid(), 3, 'CREDIT', 25000.0000, 25000.0000, 'Initial deposit'),
    (gen_random_uuid(), 4, 'CREDIT', 15000.0000, 15000.0000, 'Initial deposit'),
    (gen_random_uuid(), 5, 'CREDIT', 8000.0000, 8000.0000, 'Initial deposit');
