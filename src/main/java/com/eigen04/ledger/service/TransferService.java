package com.eigen04.ledger.service;

import com.eigen04.ledger.dto.request.DepositRequest;
import com.eigen04.ledger.dto.request.TransferRequest;
import com.eigen04.ledger.dto.response.LedgerEntryResponse;
import com.eigen04.ledger.dto.response.TransferResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransferService {

    TransferResponse executeTransfer(TransferRequest request);

    TransferResponse deposit(DepositRequest request);

    Page<LedgerEntryResponse> getAccountLedger(Long accountId, Pageable pageable);

    Page<TransferResponse> getTransferHistory(Long accountId, Pageable pageable);
}
