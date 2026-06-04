package com.seafood.shared.error;

/**
 * 资源未找到(→ HTTP 404 / code=NOT_FOUND)。
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
