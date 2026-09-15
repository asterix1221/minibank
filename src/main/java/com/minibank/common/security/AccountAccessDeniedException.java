package com.minibank.common.security;

/**
 * Thrown by the service layer when an authenticated client attempts to operate
 * on an account that does not belong to them. This is a domain-level authorization
 * check (Account.clientId == current clientId) that sits on top of, and is
 * independent from, Spring Security's authentication. Maps to HTTP 403.
 */
public class AccountAccessDeniedException extends RuntimeException {

    public AccountAccessDeniedException(String message) {
        super(message);
    }
}
