package com.minibank.transaction.repository;

import com.minibank.transaction.entity.TransactionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionEventRepository extends JpaRepository<TransactionEvent, UUID> {

    List<TransactionEvent> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
}
