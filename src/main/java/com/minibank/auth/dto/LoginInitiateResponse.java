package com.minibank.auth.dto;

import java.util.UUID;

public record LoginInitiateResponse(
        UUID sessionId,
        String debugCode
) {
}
