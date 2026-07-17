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

        // code-review 发现(2026-07-17):此前只把 404 映射 NOT_FOUND，其余一律
        // INTERNAL——filter 层的 URL 级鉴权拒绝（SecurityConfig 的 authenticated()
        // 规则，不是方法级 @PreAuthorize，见 GlobalExceptionHandler.accessDenied()
        // 的 javadoc）从未经过任何 @ExceptionHandler，直接被 Spring Security
        // 转发到 /error，命中本类——状态码对（403），但 code 字段说谎（说
        // INTERNAL，实际是 FORBIDDEN），会让按 code 分流的客户端把一次routine
        // 的鉴权拒绝误判成服务器崩了。补 403 分支，与
        // GlobalExceptionHandler.accessDenied() 用同一个 code/message，两条路径
        // 契约保持一致。
        String code;
        String message;
        if (status == HttpStatus.NOT_FOUND.value()) {
            code = ErrorResponse.CODE_NOT_FOUND;
            message = ErrorResponse.MESSAGE_NOT_FOUND;
        } else if (status == HttpStatus.FORBIDDEN.value()) {
            code = ErrorResponse.CODE_FORBIDDEN;
            message = ErrorResponse.MESSAGE_FORBIDDEN;
        } else {
            code = ErrorResponse.CODE_INTERNAL;
            message = ErrorResponse.MESSAGE_INTERNAL;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return body;
    }
}
