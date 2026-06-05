package com.seafood.shared.error;

import com.seafood.shared.security.RateLimitedException;
import com.seafood.user.application.AccountLockedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常 → ErrorResponse 转换(参见 design.md §5.3)。
 *
 * <p>Sprint 2 增:
 * <ul>
 *   <li>{@link RateLimitedException} → HTTP 429 + Retry-After + {@code code=RATE_LIMITED}
 *       (C2,specs/runtime-security §Admin endpoints enforce a rate limit)</li>
 *   <li>{@link AccountLockedException} → HTTP 423 + Retry-After + {@code code=ACCOUNT_LOCKED}
 *       (C3 §3.10,specs/auth §Login lockout)</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return ResponseEntity.status(404)
                .body(new ErrorResponse("NOT_FOUND", e.getMessage(), null));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> validation(ValidationException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION", e.getMessage(), e.getFieldErrors()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> domain(DomainException e) {
        return ResponseEntity.status(409)
                .body(new ErrorResponse("DOMAIN", e.getMessage(), null));
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<ErrorResponse> rateLimited(RateLimitedException e) {
        return ResponseEntity.status(429)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(new ErrorResponse("RATE_LIMITED", e.getMessage(), null));
    }

    /**
     * Sprint 2 §3.10 — 账号被锁 → HTTP 423 Locked(WEBDAV 状态码) + Retry-After +
     * {@code code=ACCOUNT_LOCKED}。
     */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> accountLocked(AccountLockedException e) {
        return ResponseEntity.status(HttpStatus.LOCKED) // 423
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(new ErrorResponse("ACCOUNT_LOCKED", e.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> beanValidation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe ->
                fields.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION", "Request validation failed", fields));
    }
}
