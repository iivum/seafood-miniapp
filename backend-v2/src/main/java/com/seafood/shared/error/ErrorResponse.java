package com.seafood.shared.error;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    ErrorCode code,
    String message,
    Instant timestamp,
    String path,
    List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {}

    public static ErrorResponse of(ErrorCode code, String message, String path) {
        return new ErrorResponse(code, message, Instant.now(), path, null);
    }

    public static ErrorResponse of(ErrorCode code, String message, String path, List<FieldError> fieldErrors) {
        return new ErrorResponse(code, message, Instant.now(), path, fieldErrors);
    }
}
