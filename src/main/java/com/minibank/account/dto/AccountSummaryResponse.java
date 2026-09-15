package com.minibank.account.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * NOTE (architecture decision, see README): the spec never listed a "list my accounts"
 * endpoint, but without one there is no way for a client (or the Postman collection, or a
 * Gatling scenario) to learn its own account's UUID - registration confirm only returns
 * the human-readable accountNumber, and every transfer/payment endpoint requires the UUID.
 * Same category of gap-fill as POST /templates in Stage 1: added because its absence made
 * the rest of the API untestable, not because the spec asked for it.
 */
public record AccountSummaryResponse(
        UUID id,
        String accountNumber,
        BigDecimal balance,
        String currency
) {
}
