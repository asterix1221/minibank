package com.minibank.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidConfirmationCodeException extends BusinessException {

    public InvalidConfirmationCodeException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "INVALID_CONFIRMATION_CODE";
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
