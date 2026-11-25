package com.sapidblue.ledger.repository;

import com.sapidblue.ledger.entity.TransferRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferRecordRepository extends JpaRepository<TransferRecord, Long> {

    Optional<TransferRecord> findByTransactionId(UUID transactionId);

    List<TransferRecord> findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(
            Long sourceAccountId, Long destinationAccountId);
}
