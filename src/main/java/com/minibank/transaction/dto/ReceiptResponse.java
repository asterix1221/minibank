package com.minibank.transaction.dto;

import com.minibank.transaction.entity.TransactionStatus;
import com.minibank.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReceiptResponse(
        UUID transactionId,
        TransactionType type,
        TransactionStatus status,
        UUID fromAccountId,
        UUID toAccountId,
        String externalRecipientDetails,
        BigDecimal amount,
        BigDecimal commission,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
}
