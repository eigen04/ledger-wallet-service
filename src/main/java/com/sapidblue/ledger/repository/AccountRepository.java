package com.sapidblue.ledger.repository;

import com.sapidblue.ledger.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Pessimistic write lock — SELECT ... FOR UPDATE
     * Critical for preventing race conditions in concurrent transfers.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithPessimisticLock(@Param("id") Long id);

    /**
     * Lock multiple accounts in a single query (for deterministic ordering).
     * Accounts are returned sorted by ID to enforce consistent lock ordering
     * and prevent circular deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id IN :ids ORDER BY a.id ASC")
    List<Account> findAllByIdWithPessimisticLockOrdered(@Param("ids") List<Long> ids);
}
