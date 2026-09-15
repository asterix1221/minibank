package com.minibank.transaction.service;

import com.minibank.account.entity.Account;
import com.minibank.account.repository.AccountRepository;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.entity.TransactionStatus;
import com.minibank.transaction.entity.TransactionStep;
import com.minibank.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Simulates an external payment rail finishing a PROCESSING transaction. Each transaction
 * is processed in its own transaction (called per-id from the scheduler) so one failure
 * doesn't roll back the whole batch.
 */
@Service
public class ExternalProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ExternalProcessingService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionEventRecorder eventRecorder;
    private final int failureRatePercent;

    public ExternalProcessingService(TransactionRepository transactionRepository,
                                      AccountRepository accountRepository,
                                      TransactionEventRecorder eventRecorder,
                                      @Value("${minibank.external-processing.failure-rate-percent}") int failureRatePercent) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.eventRecorder = eventRecorder;
        this.failureRatePercent = failureRatePercent;
    }

    @Transactional
    public void processOne(UUID transactionId) {
        Transaction tx = transactionRepository.findById(transactionId).orElse(null);
        if (tx == null || tx.getStatus() != TransactionStatus.PROCESSING) {
            return; // already handled concurrently or no longer relevant
        }

        boolean simulatedFailure = RANDOM.nextInt(100) < failureRatePercent;
        if (simulatedFailure) {
            tx.markFailed("External processing declined the transaction");
            Account fromAccount = accountRepository.findByIdForUpdate(tx.getFromAccountId()).orElseThrow();
            fromAccount.credit(tx.totalHold()); // refund: money had already been debited at execute()
            accountRepository.save(fromAccount);
            eventRecorder.record(tx, TransactionStep.FAILED, tx.getFailureReason());
            log.info("External processing FAILED transactionId={}", tx.getId());
        } else {
            tx.markCompleted();
            eventRecorder.record(tx, TransactionStep.SETTLED);
            log.info("External processing SETTLED transactionId={}", tx.getId());
        }
    }
}
