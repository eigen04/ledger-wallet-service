package com.sapidblue.ledger.repository;

import com.sapidblue.ledger.entity.ReconciliationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReconciliationLogRepository extends JpaRepository<ReconciliationLog, Long> {

    List<ReconciliationLog> findByAccountIdOrderByReconciledAtDesc(Long accountId);

    List<ReconciliationLog> findByIsMatchedFalse();
}
