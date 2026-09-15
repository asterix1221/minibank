package com.minibank.common.exception;

import org.springframework.http.HttpStatus;

/** Session token missing, unknown, expired, or not confirmed. Maps to 401. */
public class InvalidSessionException extends BusinessException {

    public InvalidSessionException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "INVALID_SESSION";
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.UNAUTHORIZED;
    }
}
