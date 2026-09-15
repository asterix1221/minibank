package com.minibank.transaction.dto;

import com.minibank.transaction.entity.TransactionStatus;
import com.minibank.transaction.entity.TransactionStep;

import java.time.Instant;

public record TransactionEventDto(
        TransactionStep step,
        TransactionStatus status,
        String payload,
        Instant createdAt
) {
}
