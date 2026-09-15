package com.minibank.transaction.dto;

import java.util.UUID;

public record TransactionInitiateResponse(
        UUID transactionId,
        String debugCode
) {
}
