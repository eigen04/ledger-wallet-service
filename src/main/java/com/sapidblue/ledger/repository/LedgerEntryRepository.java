package com.sapidblue.ledger.repository;

import com.sapidblue.ledger.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    /**
     * Compute the net balance from all ledger entries for an account.
     * SUM(CREDIT amounts) - SUM(DEBIT amounts) = computed balance
     * Used for reconciliation against the snapshot balance.
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN le.entryType = 'CREDIT' THEN le.amount ELSE CAST(0 AS big_decimal) END), CAST(0 AS big_decimal)) - " +
           "COALESCE(SUM(CASE WHEN le.entryType = 'DEBIT' THEN le.amount ELSE CAST(0 AS big_decimal) END), CAST(0 AS big_decimal)) " +
           "FROM LedgerEntry le WHERE le.accountId = :accountId")
    BigDecimal computeBalanceFromEntries(@Param("accountId") Long accountId);
}
