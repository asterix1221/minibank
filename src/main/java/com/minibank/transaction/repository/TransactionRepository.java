package com.minibank.transaction.repository;

import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findByStatusAndUpdatedAtBefore(TransactionStatus status, Instant threshold);
}
