package com.minibank.common.exception;

import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends BusinessException {

    public AccountNotFoundException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "ACCOUNT_NOT_FOUND";
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
