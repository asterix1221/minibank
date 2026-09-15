package com.minibank.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends BusinessException {

    public InsufficientFundsException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "INSUFFICIENT_FUNDS";
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
