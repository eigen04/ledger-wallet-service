package com.sapidblue.ledger.repository;

import com.sapidblue.ledger.entity.Account;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithPessimisticLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
    @Query("SELECT a FROM Account a WHERE a.id IN :ids ORDER BY a.id ASC")
    List<Account> findAllByIdWithPessimisticLockOrdered(@Param("ids") List<Long> ids);
}
