package com.minibank.transfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ExternalTransferInitiateRequest(
        @NotNull
        UUID fromAccountId,

        @NotBlank
        String externalRecipientDetails,

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be > 0")
        @Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount
) {
}
