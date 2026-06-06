package com.seafood.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 唯一 HTTP 安全响应头写入者(Sprint 2 §2.2,specs/runtime-security §HTTP responses
 * carry baseline security headers,design §5 decision 5)。
 *
 * <p>本 filter 把 6 个基线头写到 <em>每个</em> HTTP 响应上:
 * 静态 admin 资源、JSON API、错误响应、404 一视同仁。
 * 配合 ArchUnit 规则(2.3)防止其他类再写这 6 个头导致分散。
 *
 * <p><b>顺序由 {@code SecurityConfig} 显式声明</b>(PR review #25):
 * <pre>
 *   http.addFilterBefore(headersFilter, SecurityContextHolderFilter.class)
 * </pre>
 * <em>不</em>用 {@code @Order} — 一旦 filter 被 {@code addFilterBefore/After} 装入 Spring
 * Security 链,链内位置完全由插入点决定,@Order 对 OncePerRequestFilter 在 chain 内的实际
 * 顺序<em>不生效</em>。原 {@code @Order(HIGHEST_PRECEDENCE + 100)} 是误导性注释 —
 * 既与 SecurityConfig 冲突,又在 refactor 时容易让维护者误以为"改 @Order 就能改顺序"。
 *
 * <p>这样即便鉴权失败抛 401,响应头也已经写好。
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    public static final String HSTS = "Strict-Transport-Security";
    public static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    public static final String X_FRAME_OPTIONS = "X-Frame-Options";
    public static final String REFERRER_POLICY = "Referrer-Policy";
    public static final String PERMISSIONS_POLICY = "Permissions-Policy";
    public static final String CSP = "Content-Security-Policy";

    /** ArchUnit 规则(2.3)用同一份白名单判断"哪几个头属于 SecurityHeadersFilter 的领域"。 */
    public static final java.util.List<String> MANAGED_HEADERS = java.util.List.of(
            HSTS, X_CONTENT_TYPE_OPTIONS, X_FRAME_OPTIONS,
            REFERRER_POLICY, PERMISSIONS_POLICY, CSP);

    private final SecurityHeadersProperties props;

    public SecurityHeadersFilter(SecurityHeadersProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        // setIfAbsent 让 filter 链下游某些特殊端点(WebSocket upgrade 等)能选择退出。
        // 默认下游不会主动设置这些头,所以 setIfAbsent 在 99% 路径上等价于 setHeader。
        res.setHeader(HSTS, props.getStrictTransportSecurity());
        res.setHeader(X_CONTENT_TYPE_OPTIONS, props.getXContentTypeOptions());
        res.setHeader(X_FRAME_OPTIONS, props.getXFrameOptions());
        res.setHeader(REFERRER_POLICY, props.getReferrerPolicy());
        res.setHeader(PERMISSIONS_POLICY, props.getPermissionsPolicy());
        res.setHeader(CSP, props.getContentSecurityPolicy());
        chain.doFilter(req, res);
    }
}
