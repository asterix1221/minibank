package com.minibank.common.exception;

import org.springframework.http.HttpStatus;

/** Generic 422 for business rules that don't warrant their own dedicated exception type. */
public class BusinessRuleViolationException extends BusinessException {

    private final String errorCode;

    public BusinessRuleViolationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    @Override
    public String errorCode() {
        return errorCode;
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
