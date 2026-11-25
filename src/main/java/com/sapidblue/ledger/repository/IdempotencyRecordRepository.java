package com.sapidblue.ledger.repository;

import com.sapidblue.ledger.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * Atomically reclaims a FAILED or lease-expired IN_PROGRESS record so only one
     * retrying caller wins the execution lease.
     */
    @Modifying
    @Transactional
    @Query("UPDATE IdempotencyRecord r " +
           "SET r.status = com.sapidblue.ledger.enums.IdempotencyStatus.IN_PROGRESS, " +
           "    r.lockedUntil = :newLockedUntil, " +
           "    r.updatedAt = :now " +
           "WHERE r.idempotencyKey = :key " +
           "  AND r.requestHash = :requestHash " +
           "  AND (r.status = com.sapidblue.ledger.enums.IdempotencyStatus.FAILED " +
           "       OR (r.status = com.sapidblue.ledger.enums.IdempotencyStatus.IN_PROGRESS AND r.lockedUntil < :now))")
    int reclaimLease(@Param("key") String key,
                     @Param("requestHash") String requestHash,
                     @Param("newLockedUntil") LocalDateTime newLockedUntil,
                     @Param("now") LocalDateTime now);
}
