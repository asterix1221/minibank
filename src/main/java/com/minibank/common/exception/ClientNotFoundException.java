package com.minibank.common.exception;

import org.springframework.http.HttpStatus;

public class ClientNotFoundException extends BusinessException {

    public ClientNotFoundException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "CLIENT_NOT_FOUND";
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
