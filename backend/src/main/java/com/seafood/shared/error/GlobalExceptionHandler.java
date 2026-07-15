package com.seafood.shared.error;

import com.seafood.shared.security.RateLimitedException;
import com.seafood.user.application.AccountLockedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    /**
     * fix-error-contract-denyall PR review 发现的真实回归:未加这条前,方法级
     * {@code @PreAuthorize} 拒绝抛出的 {@code AccessDeniedException} 会被下面的
     * {@code Exception.class} 兜底吞掉，403 被错误地降级成 500——10 个
     * "*_asCustomer_returns403" 类型的既有测试实测复现了这个回归。必须显式声明
     * 比 {@code Exception.class} 更具体的 handler，把 403 语义找回来。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", "无权访问该资源", null));
    }

    /**
     * fix-error-contract-denyall:兜底,最低优先级。未被上面任何具体 handler 覆盖的
     * 异常，此前会沿调用栈往上抛，触发 Spring Boot 默认的 {@code /error} 内部重定向——
     * 而 {@code /error} 未在 {@code SecurityConfig} 白名单里，被 filter chain 末尾的
     * {@code anyRequest().denyAll()} 先一步拦成 403 空 body（真实的 500 类错误被伪装成
     * 一个语义完全不相关的授权拒绝，且丢失 {@code {code,message}} 契约）。加了这个兜底后，
     * 这类异常在 DispatcherServlet 分发阶段就被接住，根本不会走到 {@code /error} 重定向，
     * 天然绕开 denyAll 陷阱。不回传堆栈/内部细节 —— 完整异常仍走 SLF4J 正常记录。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unclassified(Exception e) {
        log.error("Unclassified exception", e);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL", "服务器内部错误", null));
    }
}
