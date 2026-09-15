package com.minibank.transaction.dto;

import jakarta.validation.constraints.NotBlank;

/** Shared body for the confirm-step of every 3/4-stage operation (transfer, payment). */
public record ConfirmationRequest(
        @NotBlank
        String code
) {
}
