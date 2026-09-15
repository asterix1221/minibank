package com.minibank.common.exception;

import org.springframework.http.HttpStatus;

/** Generic 404 for entities that don't warrant their own dedicated exception type (templates, cards, sessions). */
public class EntityNotFoundBusinessException extends BusinessException {

    private final String errorCode;

    public EntityNotFoundBusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    @Override
    public String errorCode() {
        return errorCode;
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
