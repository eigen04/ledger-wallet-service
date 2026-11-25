package com.sapidblue.ledger.controller;

import com.sapidblue.ledger.dto.request.DepositRequest;
import com.sapidblue.ledger.dto.request.TransferRequest;
import com.sapidblue.ledger.dto.response.LedgerEntryResponse;
import com.sapidblue.ledger.dto.response.TransferResponse;
import com.sapidblue.ledger.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    /**
     * Execute a transfer between two accounts.
     * Supports Idempotency-Key header for safe retries.
     */
    @PostMapping
    public ResponseEntity<TransferResponse> executeTransfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        log.info("POST /api/v1/transfers - {} -> {} amount: {} [idempotencyKey={}]",
                request.getSourceAccountId(),
                request.getDestinationAccountId(),
                request.getAmount(),
                idempotencyKey);
        TransferResponse response = transferService.executeTransfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Deposit funds into an account.
     */
    @PostMapping("/deposit")
    public ResponseEntity<TransferResponse> deposit(
            @Valid @RequestBody DepositRequest request) {
        log.info("POST /api/v1/transfers/deposit - account: {} amount: {}",
                request.getAccountId(), request.getAmount());
        TransferResponse response = transferService.deposit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get ledger entries (transaction history) for an account.
     */
    @GetMapping("/ledger/{accountId}")
    public ResponseEntity<List<LedgerEntryResponse>> getAccountLedger(@PathVariable Long accountId) {
        log.info("GET /api/v1/transfers/ledger/{}", accountId);
        return ResponseEntity.ok(transferService.getAccountLedger(accountId));
    }

    /**
     * Get transfer history for an account.
     */
    @GetMapping("/history/{accountId}")
    public ResponseEntity<List<TransferResponse>> getTransferHistory(@PathVariable Long accountId) {
        log.info("GET /api/v1/transfers/history/{}", accountId);
        return ResponseEntity.ok(transferService.getTransferHistory(accountId));
    }
}
