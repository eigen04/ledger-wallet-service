package com.sapidblue.ledger.service;

import com.sapidblue.ledger.dto.request.DepositRequest;
import com.sapidblue.ledger.dto.request.TransferRequest;
import com.sapidblue.ledger.dto.response.LedgerEntryResponse;
import com.sapidblue.ledger.dto.response.TransferResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransferService {

    TransferResponse executeTransfer(TransferRequest request);

    TransferResponse deposit(DepositRequest request);

    Page<LedgerEntryResponse> getAccountLedger(Long accountId, Pageable pageable);

    Page<TransferResponse> getTransferHistory(Long accountId, Pageable pageable);
}
