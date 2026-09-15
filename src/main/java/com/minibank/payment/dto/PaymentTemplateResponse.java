package com.minibank.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentTemplateResponse(
        UUID id,
        String name,
        String recipientDetails,
        BigDecimal defaultAmount,
        String category
) {
}
