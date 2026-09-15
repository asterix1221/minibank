package com.minibank.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for all business/domain exceptions. Each subtype carries its own
 * error code (used in the API error body) and the HTTP status it maps to.
 */
public abstract class BusinessException extends RuntimeException {

    protected BusinessException(String message) {
        super(message);
    }

    public abstract String errorCode();

    public abstract HttpStatus httpStatus();
}
