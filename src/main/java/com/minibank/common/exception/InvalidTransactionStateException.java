package com.minibank.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidTransactionStateException extends BusinessException {

    public InvalidTransactionStateException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "INVALID_TRANSACTION_STATE";
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.CONFLICT;
    }
}
