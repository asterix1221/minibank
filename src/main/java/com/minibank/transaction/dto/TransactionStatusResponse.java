package com.minibank.transaction.dto;

import com.minibank.transaction.entity.TransactionStatus;

import java.util.UUID;

public record TransactionStatusResponse(
        UUID transactionId,
        TransactionStatus status,
        String failureReason
) {
}
