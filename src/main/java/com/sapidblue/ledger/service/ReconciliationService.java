package com.sapidblue.ledger.service;

import com.sapidblue.ledger.dto.response.ReconciliationResponse;

import java.util.List;

public interface ReconciliationService {

    /**
     * Reconcile a single account: verify that the snapshot balance matches
     * the computed balance from all historical ledger entries.
     */
    ReconciliationResponse reconcileAccount(Long accountId);

    /**
     * Reconcile all accounts in the system.
     */
    List<ReconciliationResponse> reconcileAll();
}
