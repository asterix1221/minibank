package com.minibank.account.util;

import java.security.SecureRandom;

/** Generates a plausible 20-digit account number (RU-style, no real bank routing meaning). */
public final class AccountNumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountNumberGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder("408");
        for (int i = 0; i < 17; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
