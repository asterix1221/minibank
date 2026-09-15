package com.minibank.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginInitiateRequest(
        @NotBlank
        @Pattern(regexp = "^\\+7\\d{10}$", message = "phone must match +7XXXXXXXXXX")
        String phone
) {
}
