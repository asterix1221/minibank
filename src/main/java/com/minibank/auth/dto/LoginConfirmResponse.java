package com.minibank.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record LoginConfirmResponse(
        UUID token,
        Instant expiresAt
) {
}
