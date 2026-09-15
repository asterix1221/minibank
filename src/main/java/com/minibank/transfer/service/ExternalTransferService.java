package com.minibank.transfer.service;

import com.minibank.account.entity.Account;
import com.minibank.account.repository.AccountRepository;
import com.minibank.account.util.VerificationCodeGenerator;
import com.minibank.common.exception.AccountNotFoundException;
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
import com.minibank.transaction.repository.TransactionRepository;
import com.minibank.transaction.service.TransactionEventRecorder;
import com.minibank.transaction.service.TransactionLifecycleService;
import com.minibank.transfer.dto.ExternalTransferInitiateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class ExternalTransferService {

    private static final Logger log = LoggerFactory.getLogger(ExternalTransferService.class);
    private static final TransactionType TYPE = TransactionType.TRANSFER_EXTERNAL;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionEventRecorder eventRecorder;
    private final TransactionLifecycleService lifecycle;
    private final CurrentClientProvider currentClientProvider;
    private final BusinessMetrics businessMetrics;
    private final BigDecimal commissionPercent;

    public ExternalTransferService(AccountRepository accountRepository,
                                    TransactionRepository transactionRepository,
                                    TransactionEventRecorder eventRecorder,
                                    TransactionLifecycleService lifecycle,
                                    CurrentClientProvider currentClientProvider,
                                    BusinessMetrics businessMetrics,
                                    @Value("${minibank.transfer.external-commission-percent}") BigDecimal commissionPercent) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.eventRecorder = eventRecorder;
        this.lifecycle = lifecycle;
        this.currentClientProvider = currentClientProvider;
        this.businessMetrics = businessMetrics;
        this.commissionPercent = commissionPercent;
    }

    @Transactional
    public TransactionInitiateResponse initiate(ExternalTransferInitiateRequest request, String idempotencyKey) {
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

            BigDecimal commission = request.amount().multiply(commissionPercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal totalHold = request.amount().add(commission);

            Account lockedFrom = accountRepository.findByIdForUpdate(fromAccount.getId()).orElseThrow();
            if (lockedFrom.availableBalance().compareTo(totalHold) < 0) {
                throw new InsufficientFundsException("Insufficient available balance for amount + commission");
            }
            lockedFrom.hold(totalHold);
            accountRepository.save(lockedFrom);

            String code = VerificationCodeGenerator.generate();
            Transaction tx = Transaction.createPending(TYPE, clientId,
                    fromAccount.getId(), null, request.externalRecipientDetails(), null,
                    request.amount(), commission, code, idempotencyKey);
            tx = transactionRepository.save(tx);
            eventRecorder.record(tx, TransactionStep.INITIATED);

            log.info("External transfer initiated: transactionId={}, from={}, amount={}, commission={}",
                    tx.getId(), fromAccount.getId(), request.amount(), commission);
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

            if (tx.isTerminal() || tx.getStatus() == TransactionStatus.PROCESSING) {
                return lifecycle.toStatusResponse(tx);
            }
            if (tx.getStatus() == TransactionStatus.PENDING) {
                throw new InvalidTransactionStateException("Transaction must be confirmed before it can be executed");
            }
            if (lifecycle.expireIfExecuteWindowElapsed(tx)) {
                return lifecycle.toStatusResponse(tx);
            }

            Account fromAccount = accountRepository.findByIdForUpdate(tx.getFromAccountId()).orElseThrow();
            BigDecimal total = tx.totalHold();
            if (fromAccount.getBalance().compareTo(total) < 0) {
                tx.markFailed("Insufficient balance at execution time");
                fromAccount.releaseHold(total);
                accountRepository.save(fromAccount);
                eventRecorder.record(tx, TransactionStep.FAILED, tx.getFailureReason());
                return lifecycle.toStatusResponse(tx);
            }

            fromAccount.debit(total);
            fromAccount.releaseHold(total);
            accountRepository.save(fromAccount);

            tx.markProcessing();
            eventRecorder.record(tx, TransactionStep.EXECUTED);
            log.info("External transfer submitted for external processing: transactionId={}", tx.getId());
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
