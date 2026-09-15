package com.minibank.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * NOTE (architecture decision, see README): the spec only lists GET /templates and
 * GET /templates/{id}. Without a way to create a template, "payment by template" would
 * be untestable end-to-end, so a minimal protected POST /templates was added.
 */
public record CreatePaymentTemplateRequest(
        @NotBlank
        String name,

        @NotBlank
        String recipientDetails,

        @DecimalMin(value = "0.01", message = "defaultAmount must be > 0 if provided")
        BigDecimal defaultAmount,

        @NotBlank
        String category
) {
}
