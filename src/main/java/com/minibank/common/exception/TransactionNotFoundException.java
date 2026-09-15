package com.minibank.common.exception;

import org.springframework.http.HttpStatus;

public class TransactionNotFoundException extends BusinessException {

    public TransactionNotFoundException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "TRANSACTION_NOT_FOUND";
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
