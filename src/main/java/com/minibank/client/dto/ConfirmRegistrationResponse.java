package com.minibank.client.dto;

import java.util.UUID;

public record ConfirmRegistrationResponse(
        UUID clientId,
        String accountNumber,
        String maskedCardNumber
) {
}
