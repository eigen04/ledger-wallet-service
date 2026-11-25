-- Seed dedicated omnibus clearing account for INR deposits
INSERT INTO accounts (account_holder_name, email, available_balance, currency, status)
VALUES ('SYSTEM_TREASURY_INR', 'system.treasury.inr@ledger.internal', 1000000000.0000, 'INR', 'ACTIVE')
ON CONFLICT (email) DO NOTHING;

CREATE OR REPLACE FUNCTION verify_double_entry_balance()
RETURNS TRIGGER AS $$
DECLARE
    target_tx_id UUID;
    net_balance NUMERIC(19, 4);
BEGIN
    target_tx_id := COALESCE(NEW.transaction_id, OLD.transaction_id);

    SELECT COALESCE(SUM(
        CASE
            WHEN entry_type = 'CREDIT' THEN amount
            WHEN entry_type = 'DEBIT'  THEN -amount
            ELSE 0
        END
    ), 0)
    INTO net_balance
    FROM ledger_entries
    WHERE transaction_id = target_tx_id;

    IF net_balance <> 0 THEN
        RAISE EXCEPTION 'Double-entry invariant violated for transaction %: net sum is %',
            target_tx_id, net_balance;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Deferred until COMMIT so paired DEBIT and CREDIT rows can be inserted in sequence within one transaction.
CREATE CONSTRAINT TRIGGER trg_verify_double_entry_balance
AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION verify_double_entry_balance();
