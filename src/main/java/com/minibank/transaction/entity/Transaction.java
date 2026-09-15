package com.minibank.transaction.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * NOTE (architecture decision, see README): two fields are added beyond the spec's literal
 * field list for Transaction: `clientId` (owner, for fast access-control / idempotency
 * scoping without a join through Account) and `paymentTemplateId` (nullable, set only for
 * PAYMENT_TEMPLATE transactions so the executed payment can be traced back to its template).
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TransactionType type;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id")
    private UUID toAccountId;

    @Column(name = "external_recipient_details", length = 1000)
    private String externalRecipientDetails;

    @Column(name = "payment_template_id")
    private UUID paymentTemplateId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "commission", nullable = false, precision = 19, scale = 2)
    private BigDecimal commission;

    @Column(name = "confirmation_code", nullable = false, length = 10)
    private String confirmationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Transaction createPending(TransactionType type, UUID clientId, UUID fromAccountId,
                                              UUID toAccountId, String externalRecipientDetails,
                                              UUID paymentTemplateId, BigDecimal amount, BigDecimal commission,
                                              String confirmationCode, String idempotencyKey) {
        Transaction tx = new Transaction();
        tx.type = type;
        tx.clientId = clientId;
        tx.fromAccountId = fromAccountId;
        tx.toAccountId = toAccountId;
        tx.externalRecipientDetails = externalRecipientDetails;
        tx.paymentTemplateId = paymentTemplateId;
        tx.amount = amount;
        tx.commission = commission;
        tx.confirmationCode = confirmationCode;
        tx.status = TransactionStatus.PENDING;
        tx.idempotencyKey = idempotencyKey;
        Instant now = Instant.now();
        tx.createdAt = now;
        tx.updatedAt = now;
        return tx;
    }

    /** Total amount ever held against the source account for this transaction (amount + commission). */
    public BigDecimal totalHold() {
        return amount.add(commission);
    }

    public boolean isTerminal() {
        return status == TransactionStatus.COMPLETED || status == TransactionStatus.FAILED
                || status == TransactionStatus.EXPIRED;
    }

    public void markConfirmed() {
        this.status = TransactionStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public void markProcessing() {
        this.status = TransactionStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = TransactionStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markExpired() {
        this.status = TransactionStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }
}
