package com.minibank.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(

        @NotBlank
        String fullName,

        @NotBlank
        @Pattern(regexp = "^\\+7\\d{10}$", message = "phone must match +7XXXXXXXXXX")
        String phone,

        @NotBlank
        String passportData
) {
}
