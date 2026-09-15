package com.minibank.account.util;

import java.security.SecureRandom;

/** Generates a plausible 16-digit card number (not Luhn-validated, this is a simulated card). */
public final class CardNumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private CardNumberGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder("4276");
        for (int i = 0; i < 12; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
