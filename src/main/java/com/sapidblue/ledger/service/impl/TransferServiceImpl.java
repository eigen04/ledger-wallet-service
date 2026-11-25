package com.sapidblue.ledger.service.impl;

import com.sapidblue.ledger.dto.request.DepositRequest;
import com.sapidblue.ledger.dto.request.TransferRequest;
import com.sapidblue.ledger.dto.response.LedgerEntryResponse;
import com.sapidblue.ledger.dto.response.TransferResponse;
import com.sapidblue.ledger.entity.Account;
import com.sapidblue.ledger.entity.LedgerEntry;
import com.sapidblue.ledger.entity.TransferRecord;
import com.sapidblue.ledger.enums.EntryType;
import com.sapidblue.ledger.enums.TransferStatus;
import com.sapidblue.ledger.exception.AccountNotFoundException;
import com.sapidblue.ledger.exception.InvalidTransferException;
import com.sapidblue.ledger.repository.AccountRepository;
import com.sapidblue.ledger.repository.LedgerEntryRepository;
import com.sapidblue.ledger.repository.TransferRecordRepository;
import com.sapidblue.ledger.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransferRecordRepository transferRecordRepository;

    private static final String TREASURY_EMAIL_TEMPLATE = "system.treasury.%s@ledger.internal";

    @Override
    @Transactional
    public TransferResponse executeTransfer(TransferRequest request) {
        log.info("Executing transfer: {} -> {} amount: {}",
                request.getSourceAccountId(),
                request.getDestinationAccountId(),
                request.getAmount());

        if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
            throw new InvalidTransferException("Cannot transfer to the same account");
        }

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Transfer amount must be positive");
        }

        // Acquire row locks in ascending account ID order to prevent cyclic deadlocks
        // when concurrent transfers run in opposite directions (A -> B and B -> A).
        Long firstLockId = Math.min(request.getSourceAccountId(), request.getDestinationAccountId());
        Long secondLockId = Math.max(request.getSourceAccountId(), request.getDestinationAccountId());

        List<Account> lockedAccounts = accountRepository
                .findAllByIdWithPessimisticLockOrdered(List.of(firstLockId, secondLockId));

        if (lockedAccounts.size() != 2) {
            throw new AccountNotFoundException("One or both accounts not found");
        }

        Account sourceAccount = lockedAccounts.stream()
                .filter(a -> a.getId().equals(request.getSourceAccountId()))
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException(request.getSourceAccountId()));

        Account destAccount = lockedAccounts.stream()
                .filter(a -> a.getId().equals(request.getDestinationAccountId()))
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException(request.getDestinationAccountId()));

        sourceAccount.debit(request.getAmount(), sourceAccount.getCurrency());
        destAccount.credit(request.getAmount(), sourceAccount.getCurrency());

        accountRepository.save(sourceAccount);
        accountRepository.save(destAccount);

        UUID transactionId = UUID.randomUUID();

        LedgerEntry debitEntry = LedgerEntry.builder()
                .transactionId(transactionId)
                .accountId(sourceAccount.getId())
                .entryType(EntryType.DEBIT)
                .amount(request.getAmount())
                .balanceAfter(sourceAccount.getAvailableBalance())
                .description(request.getDescription() != null ? request.getDescription() :
                        "Transfer to account " + destAccount.getId())
                .build();
        ledgerEntryRepository.save(debitEntry);

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transactionId(transactionId)
                .accountId(destAccount.getId())
                .entryType(EntryType.CREDIT)
                .amount(request.getAmount())
                .balanceAfter(destAccount.getAvailableBalance())
                .description(request.getDescription() != null ? request.getDescription() :
                        "Transfer from account " + sourceAccount.getId())
                .build();
        ledgerEntryRepository.save(creditEntry);

        TransferRecord record = TransferRecord.builder()
                .transactionId(transactionId)
                .sourceAccountId(sourceAccount.getId())
                .destinationAccountId(destAccount.getId())
                .amount(request.getAmount())
                .currency(sourceAccount.getCurrency())
                .status(TransferStatus.COMPLETED)
                .description(request.getDescription())
                .build();
        TransferRecord saved = transferRecordRepository.save(record);

        log.info("Transfer completed: txn={}, {} -> {}, amount={}",
                transactionId, sourceAccount.getId(), destAccount.getId(), request.getAmount());

        return toTransferResponse(saved);
    }

    @Override
    @Transactional
    public TransferResponse deposit(DepositRequest request) {
        log.info("Depositing {} to account {}", request.getAmount(), request.getAccountId());

        Account targetLookup = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.getAccountId()));

        String treasuryEmail = String.format(TREASURY_EMAIL_TEMPLATE, targetLookup.getCurrency().toLowerCase());
        Account treasuryLookup = accountRepository.findByEmail(treasuryEmail)
                .orElseThrow(() -> new AccountNotFoundException(
                        "System treasury account not found for currency: " + targetLookup.getCurrency()));

        if (treasuryLookup.getId().equals(targetLookup.getId())) {
            throw new InvalidTransferException("Cannot deposit directly into system treasury account");
        }

        // Lock treasury and target in ascending ID order to avoid deadlocks with concurrent deposits
        Long firstLockId = Math.min(treasuryLookup.getId(), targetLookup.getId());
        Long secondLockId = Math.max(treasuryLookup.getId(), targetLookup.getId());

        List<Account> lockedAccounts = accountRepository
                .findAllByIdWithPessimisticLockOrdered(List.of(firstLockId, secondLockId));

        if (lockedAccounts.size() != 2) {
            throw new AccountNotFoundException("Target or treasury account not found");
        }

        Account treasuryAccount = lockedAccounts.stream()
                .filter(a -> a.getId().equals(treasuryLookup.getId()))
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException(treasuryLookup.getId()));

        Account targetAccount = lockedAccounts.stream()
                .filter(a -> a.getId().equals(targetLookup.getId()))
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException(targetLookup.getId()));

        treasuryAccount.debit(request.getAmount(), targetAccount.getCurrency());
        targetAccount.credit(request.getAmount(), targetAccount.getCurrency());

        accountRepository.save(treasuryAccount);
        accountRepository.save(targetAccount);

        UUID transactionId = UUID.randomUUID();
        String description = request.getDescription() != null ? request.getDescription() : "Deposit";

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transactionId(transactionId)
                .accountId(treasuryAccount.getId())
                .entryType(EntryType.DEBIT)
                .amount(request.getAmount())
                .balanceAfter(treasuryAccount.getAvailableBalance())
                .description(description)
                .build());

        ledgerEntryRepository.save(LedgerEntry.builder()
                .transactionId(transactionId)
                .accountId(targetAccount.getId())
                .entryType(EntryType.CREDIT)
                .amount(request.getAmount())
                .balanceAfter(targetAccount.getAvailableBalance())
                .description(description)
                .build());

        TransferRecord saved = transferRecordRepository.save(TransferRecord.builder()
                .transactionId(transactionId)
                .sourceAccountId(treasuryAccount.getId())
                .destinationAccountId(targetAccount.getId())
                .amount(request.getAmount())
                .currency(targetAccount.getCurrency())
                .status(TransferStatus.COMPLETED)
                .description(description)
                .build());

        log.info("Deposit completed: txn={}, account={}, amount={}",
                transactionId, targetAccount.getId(), request.getAmount());

        return toTransferResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getAccountLedger(Long accountId) {
        // Verify account exists
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }

        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(this::toLedgerEntryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransferResponse> getTransferHistory(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }

        return transferRecordRepository
                .findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(accountId, accountId)
                .stream()
                .map(this::toTransferResponse)
                .toList();
    }

    private TransferResponse toTransferResponse(TransferRecord record) {
        return TransferResponse.builder()
                .id(record.getId())
                .transactionId(record.getTransactionId())
                .sourceAccountId(record.getSourceAccountId())
                .destinationAccountId(record.getDestinationAccountId())
                .amount(record.getAmount())
                .currency(record.getCurrency())
                .status(record.getStatus())
                .description(record.getDescription())
                .createdAt(record.getCreatedAt())
                .build();
    }

    private LedgerEntryResponse toLedgerEntryResponse(LedgerEntry entry) {
        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .transactionId(entry.getTransactionId())
                .accountId(entry.getAccountId())
                .entryType(entry.getEntryType())
                .amount(entry.getAmount())
                .balanceAfter(entry.getBalanceAfter())
                .description(entry.getDescription())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
