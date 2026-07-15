package com.seafood.shared.error;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * fix-error-contract-denyall 决策 2(防御纵深):{@code GlobalExceptionHandler} 的
 * 兜底 {@code @ExceptionHandler(Exception.class)} 已经接住绝大多数场景（控制器/服务/
 * 仓库调用栈内同步抛出的异常）。本类只覆盖 ControllerAdvice 覆盖不到的窄场景——异常
 * 来自 filter 层，或 DispatcherServlet 找不到匹配 handler（404）——这些会走 Spring
 * Boot 默认的 {@code /error} 内部转发，此时渲染出的 body 也必须是
 * {@code {code,message}} 契约形状，而不是 Spring Boot 默认的
 * {@code {timestamp,status,error,path}} 形状。刻意保持最小化：不回传堆栈/请求路径等
 * 内部细节（design.md Non-Goals）。
 */
@Component
public class ContractErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> defaults = super.getErrorAttributes(webRequest, options);
        int status = (int) defaults.getOrDefault("status", 500);
        String code = (status == HttpStatus.NOT_FOUND.value()) ? "NOT_FOUND" : "INTERNAL";
        String message = (status == HttpStatus.NOT_FOUND.value()) ? "资源不存在" : "服务器内部错误";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return body;
    }
}
