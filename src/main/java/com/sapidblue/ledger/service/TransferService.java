package com.sapidblue.ledger.service;

import com.sapidblue.ledger.dto.request.DepositRequest;
import com.sapidblue.ledger.dto.request.TransferRequest;
import com.sapidblue.ledger.dto.response.LedgerEntryResponse;
import com.sapidblue.ledger.dto.response.TransferResponse;

import java.util.List;

public interface TransferService {

    /**
     * Execute a transfer between two accounts with full double-entry bookkeeping,
     * pessimistic locking, and deterministic lock ordering to prevent deadlocks.
     */
    TransferResponse executeTransfer(TransferRequest request);

    /**
     * Deposit funds into an account (single-leg credit entry).
     */
    TransferResponse deposit(DepositRequest request);

    /**
     * Get all ledger entries (transaction history) for an account.
     */
    List<LedgerEntryResponse> getAccountLedger(Long accountId);

    /**
     * Get all transfer records involving an account.
     */
    List<TransferResponse> getTransferHistory(Long accountId);
}
