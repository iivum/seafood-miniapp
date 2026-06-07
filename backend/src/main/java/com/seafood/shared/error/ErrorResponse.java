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
}
