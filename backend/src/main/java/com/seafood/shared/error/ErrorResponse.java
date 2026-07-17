package com.seafood.shared.error;

import java.util.Map;

/**
 * 统一错误响应(对齐 specs/backend-api §Uniform error responses)。
 *
 * @param code      稳定错误码,前端按 code 国际化
 * @param message   人类可读描述
 * @param fieldErrors 字段级错误(VALIDATION 时填充)
 */
public record ErrorResponse(String code, String message, Map<String, String> fieldErrors) {

    /**
     * code-review 发现(2026-07-17):{@link GlobalExceptionHandler} 与
     * {@link ContractErrorAttributes} 是两条独立的错误渲染路径(前者接
     * ControllerAdvice 覆盖到的异常,后者接 filter 层/无 handler 兜底到
     * {@code /error} 的场景)。{@code FORBIDDEN}/{@code INTERNAL} 两条路径都会用到
     * 完全相同的 code + 通用 message(此前各自手写字面量,容易改一处忘改另一处
     * 悄悄漂移),抽成共享常量。{@code NOT_FOUND} 的 code 值同样两处共用,但
     * message 只有 {@link ContractErrorAttributes} 的兜底场景用得到(
     * {@link GlobalExceptionHandler#notFound} 用的是具体异常自带的 message,
     * 不是这个通用兜底文案)。
     */
    public static final String CODE_NOT_FOUND = "NOT_FOUND";
    public static final String MESSAGE_NOT_FOUND = "资源不存在";
    public static final String CODE_FORBIDDEN = "FORBIDDEN";
    public static final String MESSAGE_FORBIDDEN = "无权访问该资源";
    public static final String CODE_INTERNAL = "INTERNAL";
    public static final String MESSAGE_INTERNAL = "服务器内部错误";
}
