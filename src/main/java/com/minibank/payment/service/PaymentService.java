package com.minibank.payment.service;

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
import com.minibank.payment.dto.PaymentInitiateRequest;
import com.minibank.payment.entity.PaymentTemplate;
import com.minibank.transaction.dto.ConfirmationRequest;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final TransactionType TYPE = TransactionType.PAYMENT_TEMPLATE;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionEventRecorder eventRecorder;
    private final TransactionLifecycleService lifecycle;
    private final CurrentClientProvider currentClientProvider;
    private final PaymentTemplateService templateService;
    private final BusinessMetrics businessMetrics;

    public PaymentService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           TransactionEventRecorder eventRecorder,
                           TransactionLifecycleService lifecycle,
                           CurrentClientProvider currentClientProvider,
                           PaymentTemplateService templateService,
                           BusinessMetrics businessMetrics) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.eventRecorder = eventRecorder;
        this.lifecycle = lifecycle;
        this.currentClientProvider = currentClientProvider;
        this.templateService = templateService;
        this.businessMetrics = businessMetrics;
    }

    @Transactional
    public TransactionInitiateResponse initiate(UUID templateId, PaymentInitiateRequest request, String idempotencyKey) {
        return businessMetrics.timed("initiate", TYPE, () -> {
            var existing = lifecycle.findExistingByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                Transaction tx = existing.get();
                return new TransactionInitiateResponse(tx.getId(), tx.getConfirmationCode());
            }

            UUID clientId = currentClientProvider.getCurrentClientId();
            PaymentTemplate template = templateService.getOwnedOrThrow(templateId);

            BigDecimal amount = request.amount() != null ? request.amount() : template.getDefaultAmount();
            if (amount == null) {
                throw new BusinessRuleViolationException("AMOUNT_REQUIRED",
                        "Template has no default amount, amount must be provided");
            }

            Account fromAccount = accountRepository.findById(request.fromAccountId())
                    .orElseThrow(() -> new AccountNotFoundException("Source account not found"));
            if (!fromAccount.getClientId().equals(clientId)) {
                throw new AccountAccessDeniedException("Source account does not belong to the current client");
            }

            Account lockedFrom = accountRepository.findByIdForUpdate(fromAccount.getId()).orElseThrow();
            if (lockedFrom.availableBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException("Insufficient available balance on source account");
            }
            lockedFrom.hold(amount);
            accountRepository.save(lockedFrom);

            String code = VerificationCodeGenerator.generate();
            Transaction tx = Transaction.createPending(TYPE, clientId,
                    fromAccount.getId(), null, template.getRecipientDetails(), template.getId(),
                    amount, BigDecimal.ZERO.setScale(2), code, idempotencyKey);
            tx = transactionRepository.save(tx);
            eventRecorder.record(tx, TransactionStep.INITIATED);

            log.info("Template payment initiated: transactionId={}, templateId={}, amount={}", tx.getId(), templateId, amount);
            return new TransactionInitiateResponse(tx.getId(), code);
        });
    }

    @Transactional(noRollbackFor = com.minibank.common.exception.InvalidConfirmationCodeException.class)
    public TransactionStatusResponse confirm(UUID transactionId, ConfirmationRequest request) {
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
            if (fromAccount.getBalance().compareTo(tx.getAmount()) < 0) {
                tx.markFailed("Insufficient balance at execution time");
                fromAccount.releaseHold(tx.getAmount());
                accountRepository.save(fromAccount);
                eventRecorder.record(tx, TransactionStep.FAILED, tx.getFailureReason());
                return lifecycle.toStatusResponse(tx);
            }

            fromAccount.debit(tx.getAmount());
            fromAccount.releaseHold(tx.getAmount());
            accountRepository.save(fromAccount);

            tx.markProcessing();
            eventRecorder.record(tx, TransactionStep.EXECUTED);
            log.info("Template payment submitted for external processing: transactionId={}", tx.getId());
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
