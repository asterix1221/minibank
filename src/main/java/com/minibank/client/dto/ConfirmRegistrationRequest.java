package com.minibank.client.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmRegistrationRequest(
        @NotBlank
        String code
) {
}
