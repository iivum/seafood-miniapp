package com.seafood.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, e.code(), e.getMessage(), req, null);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> validation(ValidationException e, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, e.code(), e.getMessage(), req, e.fieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> beanValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<ErrorResponse.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
            .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "请求参数校验失败", req, fields);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> domain(DomainException e, HttpServletRequest req) {
        HttpStatus status = switch (e.code()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case CONFLICT, DOMAIN -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return build(status, e.code(), e.getMessage(), req, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> badCredentials(BadCredentialsException e, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "用户名或密码错误", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(AccessDeniedException e, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "无权访问", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> fallback(Exception e, HttpServletRequest req) {
        log.error("Unhandled exception at {}", req.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL, "服务器内部错误", req, null);
    }

    private ResponseEntity<ErrorResponse> build(
        HttpStatus status, ErrorCode code, String msg, HttpServletRequest req, List<ErrorResponse.FieldError> fields
    ) {
        return ResponseEntity.status(status)
            .body(ErrorResponse.of(code, msg, req.getRequestURI(), fields));
    }
}
