package com.eigen04.ledger.repository;

import com.eigen04.ledger.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Transactional
    @Query("UPDATE IdempotencyRecord r " +
           "SET r.status = com.eigen04.ledger.enums.IdempotencyStatus.IN_PROGRESS, " +
           "    r.lockedUntil = :newLockedUntil, " +
           "    r.updatedAt = :now " +
           "WHERE r.idempotencyKey = :key " +
           "  AND r.requestHash = :requestHash " +
           "  AND (r.status = com.eigen04.ledger.enums.IdempotencyStatus.FAILED " +
           "       OR (r.status = com.eigen04.ledger.enums.IdempotencyStatus.IN_PROGRESS AND r.lockedUntil < :now))")
    int reclaimLease(@Param("key") String key,
                     @Param("requestHash") String requestHash,
                     @Param("newLockedUntil") Instant newLockedUntil,
                     @Param("now") Instant now);

    // Fence completion/failure updates with the exact lease timestamp and active expiry
    // so a stalled thread cannot overwrite a lease reclaimed by another caller.
    @Modifying
    @Transactional
    @Query("UPDATE IdempotencyRecord r " +
           "SET r.status = com.eigen04.ledger.enums.IdempotencyStatus.COMPLETED, " +
           "    r.responseStatus = :responseStatus, " +
           "    r.responseBody = :responseBody, " +
           "    r.lockedUntil = null, " +
           "    r.updatedAt = :now " +
           "WHERE r.idempotencyKey = :key " +
           "  AND r.status = com.eigen04.ledger.enums.IdempotencyStatus.IN_PROGRESS " +
           "  AND r.lockedUntil = :expectedLockedUntil " +
           "  AND r.lockedUntil > :now")
    int markCompletedIfLeaseHeld(@Param("key") String key,
                                 @Param("expectedLockedUntil") Instant expectedLockedUntil,
                                 @Param("responseStatus") Integer responseStatus,
                                 @Param("responseBody") String responseBody,
                                 @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("UPDATE IdempotencyRecord r " +
           "SET r.status = com.eigen04.ledger.enums.IdempotencyStatus.FAILED, " +
           "    r.lockedUntil = null, " +
           "    r.updatedAt = :now " +
           "WHERE r.idempotencyKey = :key " +
           "  AND r.status = com.eigen04.ledger.enums.IdempotencyStatus.IN_PROGRESS " +
           "  AND r.lockedUntil = :expectedLockedUntil " +
           "  AND r.lockedUntil > :now")
    int markFailedIfLeaseHeld(@Param("key") String key,
                              @Param("expectedLockedUntil") Instant expectedLockedUntil,
                              @Param("now") Instant now);
}
