UPDATE accounts a
SET available_balance = 0.0000
WHERE a.email = 'system.treasury.inr@ledger.internal'
  AND NOT EXISTS (
      SELECT 1 FROM ledger_entries le WHERE le.account_id = a.id
  );
