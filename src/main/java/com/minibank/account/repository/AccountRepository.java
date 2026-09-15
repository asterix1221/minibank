package com.minibank.account.repository;

import com.minibank.account.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByClientId(UUID clientId);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Pessimistic write lock for the duration of the enclosing @Transactional method.
     * Used before any balance mutation (hold/release/debit/credit) so concurrent
     * transfers touching the same account serialize instead of racing on the read-modify-write.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(UUID id);
}
