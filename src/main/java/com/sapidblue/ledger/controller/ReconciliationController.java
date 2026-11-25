package com.sapidblue.ledger.controller;

import com.sapidblue.ledger.dto.response.ReconciliationResponse;
import com.sapidblue.ledger.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    /**
     * Reconcile a single account.
     */
    @PostMapping("/{accountId}")
    public ResponseEntity<ReconciliationResponse> reconcileAccount(@PathVariable Long accountId) {
        log.info("POST /api/v1/reconciliation/{}", accountId);
        return ResponseEntity.ok(reconciliationService.reconcileAccount(accountId));
    }

    /**
     * Reconcile all accounts in the system.
     */
    @PostMapping("/all")
    public ResponseEntity<List<ReconciliationResponse>> reconcileAll() {
        log.info("POST /api/v1/reconciliation/all");
        return ResponseEntity.ok(reconciliationService.reconcileAll());
    }
}
