package com.eigen04.ledger.repository;

import com.eigen04.ledger.entity.ReconciliationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReconciliationLogRepository extends JpaRepository<ReconciliationLog, Long> {

    List<ReconciliationLog> findByAccountIdOrderByReconciledAtDesc(Long accountId);

    List<ReconciliationLog> findByIsMatchedFalse();
}
