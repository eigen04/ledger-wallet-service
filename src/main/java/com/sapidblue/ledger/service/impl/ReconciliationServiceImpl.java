package com.sapidblue.ledger.service.impl;

import com.sapidblue.ledger.dto.response.ReconciliationResponse;
import com.sapidblue.ledger.entity.Account;
import com.sapidblue.ledger.entity.ReconciliationLog;
import com.sapidblue.ledger.exception.AccountNotFoundException;
import com.sapidblue.ledger.repository.AccountRepository;
import com.sapidblue.ledger.repository.LedgerEntryRepository;
import com.sapidblue.ledger.repository.ReconciliationLogRepository;
import com.sapidblue.ledger.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ReconciliationLogRepository reconciliationLogRepository;

    // REPEATABLE_READ ensures accounts and ledger_entries are read from the same MVCC snapshot,
    // preventing false-positive drift alerts during concurrent transfers.
    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ReconciliationResponse reconcileAccount(Long accountId) {
        log.info("Reconciling account: {}", accountId);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        BigDecimal snapshotBalance = account.getAvailableBalance().add(account.getLockedBalance());
        BigDecimal computedBalance = ledgerEntryRepository.computeBalanceFromEntries(accountId);

        if (computedBalance == null) {
            computedBalance = BigDecimal.ZERO;
        }

        BigDecimal discrepancy = snapshotBalance.subtract(computedBalance).abs();
        boolean isMatched = discrepancy.compareTo(BigDecimal.ZERO) == 0;

        ReconciliationLog logEntry = ReconciliationLog.builder()
                .accountId(accountId)
                .snapshotBalance(snapshotBalance)
                .computedBalance(computedBalance)
                .isMatched(isMatched)
                .discrepancy(discrepancy)
                .build();

        reconciliationLogRepository.save(logEntry);

        if (!isMatched) {
            log.error("RECONCILIATION MISMATCH for account {}: snapshot={}, computed={}, discrepancy={}",
                    accountId, snapshotBalance, computedBalance, discrepancy);
        } else {
            log.info("Account {} reconciled successfully. Balance: {}", accountId, snapshotBalance);
        }

        return toResponse(logEntry);
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<ReconciliationResponse> reconcileAll() {
        log.info("Starting full system reconciliation...");

        List<Account> allAccounts = accountRepository.findAll();

        List<ReconciliationResponse> results = allAccounts.stream()
                .map(account -> reconcileAccount(account.getId()))
                .toList();

        long mismatches = results.stream()
                .filter(r -> !r.getIsMatched())
                .count();

        log.info("Reconciliation complete. Total accounts: {}, Mismatches: {}",
                results.size(), mismatches);

        return results;
    }

    private ReconciliationResponse toResponse(ReconciliationLog logEntry) {
        return ReconciliationResponse.builder()
                .accountId(logEntry.getAccountId())
                .snapshotBalance(logEntry.getSnapshotBalance())
                .computedBalance(logEntry.getComputedBalance())
                .isMatched(logEntry.getIsMatched())
                .discrepancy(logEntry.getDiscrepancy())
                .reconciledAt(logEntry.getReconciledAt())
                .build();
    }
}
