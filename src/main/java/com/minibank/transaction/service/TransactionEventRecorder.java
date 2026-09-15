package com.minibank.transaction.service;

import com.minibank.common.metrics.BusinessMetrics;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.entity.TransactionEvent;
import com.minibank.transaction.entity.TransactionStatus;
import com.minibank.transaction.entity.TransactionStep;
import com.minibank.transaction.repository.TransactionEventRepository;
import org.springframework.stereotype.Component;

/**
 * Every Transaction status change must write a TransactionEvent in the same DB transaction
 * (per spec). Centralised here so no service forgets to do it - and, since every transition
 * already passes through here, this is also where the transactions_total business metric
 * is incremented once a transaction reaches a terminal status.
 */
@Component
public class TransactionEventRecorder {

    private final TransactionEventRepository transactionEventRepository;
    private final BusinessMetrics businessMetrics;

    public TransactionEventRecorder(TransactionEventRepository transactionEventRepository,
                                     BusinessMetrics businessMetrics) {
        this.transactionEventRepository = transactionEventRepository;
        this.businessMetrics = businessMetrics;
    }

    public void record(Transaction transaction, TransactionStep step, String payload) {
        transactionEventRepository.save(
                TransactionEvent.of(transaction.getId(), step, transaction.getStatus(), payload));

        TransactionStatus status = transaction.getStatus();
        if (status == TransactionStatus.COMPLETED || status == TransactionStatus.FAILED) {
            businessMetrics.recordTransactionOutcome(transaction.getType(), status);
        }
    }

    public void record(Transaction transaction, TransactionStep step) {
        record(transaction, step, null);
    }
}
