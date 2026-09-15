package com.minibank.transfer.service;

import com.minibank.account.entity.Account;
import com.minibank.account.repository.AccountRepository;
import com.minibank.account.util.VerificationCodeGenerator;
import com.minibank.common.exception.AccountNotFoundException;
import com.minibank.common.exception.BusinessRuleViolationException;
import com.minibank.common.exception.InsufficientFundsException;
import com.minibank.common.exception.InvalidTransactionStateException;
import com.minibank.common.metrics.BusinessMetrics;
import com.minibank.common.security.AccountAccessDeniedException;
import com.minibank.common.security.CurrentClientProvider;
import com.minibank.transaction.dto.ReceiptResponse;
import com.minibank.transaction.dto.TransactionInitiateResponse;
import com.minibank.transaction.dto.TransactionStatusResponse;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.entity.TransactionStatus;
import com.minibank.transaction.entity.TransactionStep;
import com.minibank.transaction.entity.TransactionType;
import com.minibank.transaction.service.TransactionEventRecorder;
import com.minibank.transaction.service.TransactionLifecycleService;
import com.minibank.transfer.dto.InternalTransferInitiateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class InternalTransferService {

    private static final Logger log = LoggerFactory.getLogger(InternalTransferService.class);
    private static final TransactionType TYPE = TransactionType.TRANSFER_INTERNAL;

    private final AccountRepository accountRepository;
    private final TransactionEventRecorder eventRecorder;
    private final TransactionLifecycleService lifecycle;
    private final CurrentClientProvider currentClientProvider;
    private final BusinessMetrics businessMetrics;
    private final com.minibank.transaction.repository.TransactionRepository transactionRepository;

    public InternalTransferService(AccountRepository accountRepository,
                                    TransactionEventRecorder eventRecorder,
                                    TransactionLifecycleService lifecycle,
                                    CurrentClientProvider currentClientProvider,
                                    BusinessMetrics businessMetrics,
                                    com.minibank.transaction.repository.TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.eventRecorder = eventRecorder;
        this.lifecycle = lifecycle;
        this.currentClientProvider = currentClientProvider;
        this.businessMetrics = businessMetrics;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionInitiateResponse initiate(InternalTransferInitiateRequest request, String idempotencyKey) {
        return businessMetrics.timed("initiate", TYPE, () -> {
            var existing = lifecycle.findExistingByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Transaction tx = existing.get();
                return new TransactionInitiateResponse(tx.getId(), tx.getConfirmationCode());
            }

            UUID clientId = currentClientProvider.getCurrentClientId();

            Account fromAccount = accountRepository.findById(request.fromAccountId())
                    .orElseThrow(() -> new AccountNotFoundException("Source account not found"));
            if (!fromAccount.getClientId().equals(clientId)) {
                throw new AccountAccessDeniedException("Source account does not belong to the current client");
            }

            Account toAccount = accountRepository.findByAccountNumber(request.toAccountNumber())
                    .orElseThrow(() -> new AccountNotFoundException("Destination account not found"));

            if (fromAccount.getId().equals(toAccount.getId())) {
                throw new BusinessRuleViolationException("SAME_ACCOUNT", "Cannot transfer to the same account");
            }

            Account lockedFrom = accountRepository.findByIdForUpdate(fromAccount.getId()).orElseThrow();
            if (lockedFrom.availableBalance().compareTo(request.amount()) < 0) {
                throw new InsufficientFundsException("Insufficient available balance on source account");
            }
            lockedFrom.hold(request.amount());
            accountRepository.save(lockedFrom);

            String code = VerificationCodeGenerator.generate();
            Transaction tx = Transaction.createPending(TYPE, clientId,
                    fromAccount.getId(), toAccount.getId(), null, null,
                    request.amount(), BigDecimal.ZERO.setScale(2), code, idempotencyKey);
            tx = transactionRepository.save(tx);
            eventRecorder.record(tx, TransactionStep.INITIATED);

            log.info("Internal transfer initiated: transactionId={}, from={}, to={}, amount={}",
                    tx.getId(), fromAccount.getId(), toAccount.getId(), request.amount());
            return new TransactionInitiateResponse(tx.getId(), code);
        });
    }

    @Transactional(noRollbackFor = com.minibank.common.exception.InvalidConfirmationCodeException.class)
    public TransactionStatusResponse confirm(UUID transactionId, com.minibank.transaction.dto.ConfirmationRequest request) {
        return businessMetrics.timed("confirm", TYPE, () -> lifecycle.confirm(transactionId, request));
    }

    @Transactional
    public TransactionStatusResponse execute(UUID transactionId) {
        return businessMetrics.timed("execute", TYPE, () -> {
            Transaction tx = lifecycle.getOwnedOrThrow(transactionId);

            if (tx.isTerminal()) {
                return lifecycle.toStatusResponse(tx);
            }
            if (tx.getStatus() == TransactionStatus.PENDING) {
                throw new InvalidTransactionStateException("Transaction must be confirmed before it can be executed");
            }
            if (lifecycle.expireIfExecuteWindowElapsed(tx)) {
                return lifecycle.toStatusResponse(tx);
            }

            UUID firstId = tx.getFromAccountId().compareTo(tx.getToAccountId()) <= 0 ? tx.getFromAccountId() : tx.getToAccountId();
            UUID secondId = firstId.equals(tx.getFromAccountId()) ? tx.getToAccountId() : tx.getFromAccountId();
            Account first = accountRepository.findByIdForUpdate(firstId).orElseThrow();
            Account second = accountRepository.findByIdForUpdate(secondId).orElseThrow();
            Account fromAccount = first.getId().equals(tx.getFromAccountId()) ? first : second;
            Account toAccount = first.getId().equals(tx.getToAccountId()) ? first : second;

            if (fromAccount.getBalance().compareTo(tx.getAmount()) < 0) {
                tx.markFailed("Insufficient balance at execution time");
                fromAccount.releaseHold(tx.getAmount());
                accountRepository.save(fromAccount);
                eventRecorder.record(tx, TransactionStep.FAILED, tx.getFailureReason());
                return lifecycle.toStatusResponse(tx);
            }

            fromAccount.debit(tx.getAmount());
            fromAccount.releaseHold(tx.getAmount());
            toAccount.credit(tx.getAmount());
            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);

            tx.markCompleted();
            eventRecorder.record(tx, TransactionStep.EXECUTED);
            log.info("Internal transfer executed: transactionId={}", tx.getId());
            return lifecycle.toStatusResponse(tx);
        });
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(UUID transactionId) {
        Transaction tx = lifecycle.getOwnedOrThrow(transactionId);
        return new ReceiptResponse(tx.getId(), tx.getType(), tx.getStatus(), tx.getFromAccountId(), tx.getToAccountId(),
                tx.getExternalRecipientDetails(), tx.getAmount(), tx.getCommission(), tx.getFailureReason(),
                tx.getCreatedAt(), tx.getUpdatedAt());
    }
}
