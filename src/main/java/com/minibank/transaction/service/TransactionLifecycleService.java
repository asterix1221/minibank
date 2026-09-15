package com.minibank.transaction.service;

import com.minibank.account.entity.Account;
import com.minibank.account.repository.AccountRepository;
import com.minibank.common.exception.InvalidConfirmationCodeException;
import com.minibank.common.exception.TransactionNotFoundException;
import com.minibank.common.security.AccountAccessDeniedException;
import com.minibank.common.security.CurrentClientProvider;
import com.minibank.transaction.dto.ConfirmationRequest;
import com.minibank.transaction.dto.TransactionStatusResponse;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.entity.TransactionStatus;
import com.minibank.transaction.entity.TransactionStep;
import com.minibank.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * The "confirm" stage and the PENDING/CONFIRMED-side of the state machine are identical
 * across internal transfer, external transfer and template payment - only "execute"
 * (the actual money movement) differs per type. Centralising confirm() and the expiry
 * checks here means InternalTransferService / ExternalTransferService / PaymentService
 * only need to implement the type-specific execute() logic.
 */
@Service
public class TransactionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(TransactionLifecycleService.class);

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionEventRecorder eventRecorder;
    private final CurrentClientProvider currentClientProvider;
    private final long confirmTtlMinutes;
    private final long executeTtlMinutes;

    public TransactionLifecycleService(TransactionRepository transactionRepository,
                                        AccountRepository accountRepository,
                                        TransactionEventRecorder eventRecorder,
                                        CurrentClientProvider currentClientProvider,
                                        @Value("${minibank.transaction.confirm-ttl-minutes}") long confirmTtlMinutes,
                                        @Value("${minibank.transaction.execute-ttl-minutes}") long executeTtlMinutes) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.eventRecorder = eventRecorder;
        this.currentClientProvider = currentClientProvider;
        this.confirmTtlMinutes = confirmTtlMinutes;
        this.executeTtlMinutes = executeTtlMinutes;
    }

    /** Idempotency support for initiate endpoints: replay the prior result instead of double-creating. */
    public java.util.Optional<Transaction> findExistingByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return java.util.Optional.empty();
        }
        return transactionRepository.findByIdempotencyKey(idempotencyKey);
    }

    public Transaction getOwnedOrThrow(UUID transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));
        UUID currentClientId = currentClientProvider.getCurrentClientId();
        if (!tx.getClientId().equals(currentClientId)) {
            throw new AccountAccessDeniedException("Transaction does not belong to the current client");
        }
        return tx;
    }

    @Transactional(noRollbackFor = InvalidConfirmationCodeException.class)
    public TransactionStatusResponse confirm(UUID transactionId, ConfirmationRequest request) {
        Transaction tx = getOwnedOrThrow(transactionId);

        if (tx.getStatus() != TransactionStatus.PENDING) {
            // confirm is idempotent: already confirmed/executed/terminal - just report current state
            return toStatusResponse(tx);
        }

        if (isExpired(tx.getCreatedAt(), confirmTtlMinutes)) {
            expireAndReleaseHold(tx);
            return toStatusResponse(tx);
        }

        if (!tx.getConfirmationCode().equals(request.code())) {
            tx.markFailed("Invalid confirmation code");
            releaseHold(tx.getFromAccountId(), tx.totalHold());
            eventRecorder.record(tx, TransactionStep.FAILED, tx.getFailureReason());
            log.info("Transaction {} failed confirmation: invalid code", tx.getId());
            throw new InvalidConfirmationCodeException("Provided confirmation code does not match");
        }

        tx.markConfirmed();
        eventRecorder.record(tx, TransactionStep.CONFIRMED);
        log.info("Transaction {} confirmed", tx.getId());
        return toStatusResponse(tx);
    }

    /**
     * Call at the top of every execute() implementation. Returns true (and has already
     * transitioned the transaction to EXPIRED + released its hold) if the CONFIRMED->execute
     * window has elapsed, in which case the caller should stop and just return the status.
     */
    public boolean expireIfExecuteWindowElapsed(Transaction tx) {
        if (tx.getStatus() == TransactionStatus.CONFIRMED && isExpired(tx.getUpdatedAt(), executeTtlMinutes)) {
            expireAndReleaseHold(tx);
            return true;
        }
        return false;
    }

    private void expireAndReleaseHold(Transaction tx) {
        tx.markExpired();
        releaseHold(tx.getFromAccountId(), tx.totalHold());
        eventRecorder.record(tx, TransactionStep.EXPIRED);
        log.info("Transaction {} expired", tx.getId());
    }

    private void releaseHold(UUID accountId, java.math.BigDecimal amount) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalStateException("Account referenced by transaction not found: " + accountId));
        account.releaseHold(amount);
        accountRepository.save(account);
    }

    private boolean isExpired(Instant since, long ttlMinutes) {
        return Instant.now().isAfter(since.plus(ttlMinutes, ChronoUnit.MINUTES));
    }

    public TransactionStatusResponse toStatusResponse(Transaction tx) {
        return new TransactionStatusResponse(tx.getId(), tx.getStatus(), tx.getFailureReason());
    }
}
