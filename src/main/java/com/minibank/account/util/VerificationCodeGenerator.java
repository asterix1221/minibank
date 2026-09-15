package com.minibank.account.util;

import java.security.SecureRandom;

/** Shared 6-digit confirmation code generator used by registration, login and transactions. */
public final class VerificationCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private VerificationCodeGenerator() {
    }

    public static String generate() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
