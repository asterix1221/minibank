package com.minibank.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LoginConfirmRequest(
        @NotNull
        UUID sessionId,

        @NotBlank
        String code
) {
}
