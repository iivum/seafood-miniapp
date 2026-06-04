package com.seafood.shared.error;

/**
 * 业务规则违反(对齐 specs/backend-api §Uniform error responses → HTTP 409 / code=DOMAIN)。
 */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
