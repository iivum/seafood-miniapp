package com.seafood.shared.error;

import java.util.List;

public class ValidationException extends DomainException {
    private final List<ErrorResponse.FieldError> fieldErrors;

    public ValidationException(String message, List<ErrorResponse.FieldError> fieldErrors) {
        super(ErrorCode.VALIDATION, message);
        this.fieldErrors = fieldErrors;
    }

    public List<ErrorResponse.FieldError> fieldErrors() {
        return fieldErrors;
    }
}
