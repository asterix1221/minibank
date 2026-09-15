package com.minibank.common.exception;

import java.time.Instant;

/**
 * Uniform error body for every 4xx/5xx response produced by the application.
 */
public record ErrorResponse(String error, String message, Instant timestamp) {

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, Instant.now());
    }
}
