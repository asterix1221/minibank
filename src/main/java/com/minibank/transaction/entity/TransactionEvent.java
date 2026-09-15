package com.minibank.transaction.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", nullable = false, length = 20)
    private TransactionStep step;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "payload", length = 1000)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static TransactionEvent of(UUID transactionId, TransactionStep step, TransactionStatus status, String payload) {
        TransactionEvent event = new TransactionEvent();
        event.transactionId = transactionId;
        event.step = step;
        event.status = status;
        event.payload = payload;
        event.createdAt = Instant.now();
        return event;
    }
}
