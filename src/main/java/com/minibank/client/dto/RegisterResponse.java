package com.minibank.client.dto;

import java.util.UUID;

public record RegisterResponse(
        UUID registrationId,
        String debugCode
) {
}
