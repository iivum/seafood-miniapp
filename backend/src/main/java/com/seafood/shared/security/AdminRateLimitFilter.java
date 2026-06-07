package com.seafood.shared.security;

import com.seafood.shared.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Admin 路径限流 filter(Sprint 2 §2.6,specs/runtime-security §Admin endpoints enforce
 * a rate limit)。
 *
 * <p>作用域:仅匹配 {@code /api/admin} 与 {@code /api/admin/**} 的请求(spec §"Non-admin
 * path not rate-limited")。与 {@link JwtAuthenticationFilter#isAdminPath(String)} 的
 * 判定保持一致 —— 不接受 {@code /api/adminalice} 这类近似前缀绕过。
 *
 * <p>桶 key: {@code clientIp + ":" + account}。未鉴权请求用 anonymous 占位,
 * 这样未鉴权 brute-force 仍然受同一桶限制(只是单 IP 维度的 60 rpm)。
 *
 * <p>超限时<em>直接写 429 响应</em>(不走 GlobalExceptionHandler):Filter 抛出的
 * 异常不会经过 {@code @RestControllerAdvice},标准做法是 filter 自己负责序列化
 * {@link ErrorResponse} body。{@code GlobalExceptionHandler} 仍保留
 * {@code RateLimitedException} handler,用于将来 controller 直接抛该异常的场景。
 *
 * <p><b>关于 clientIp:</b>只使用 {@link HttpServletRequest#getRemoteAddr()} —— 即 TCP
 * 层的对端 IP,完全不解析 {@code X-Forwarded-For}。理由:由 nginx/ALB 这类受信反向代理
 * 终止连接时,真实客户端 IP 已经在 TCP socket 层(代理会把 XFF 改写成连接 IP);
 * 在应用层手解 XFF 等于无差别信任请求头,攻击者只要带上
 * {@code X-Forwarded-For: 1.2.3.4} 就能绕过限流(PR review #4)。{@code application.yml}
 * 中的 {@code server.forward-headers-strategy: framework} 仍保留,用于
 * X-Forwarded-Proto / X-Forwarded-Host 这类非 IP 字段。
 */
@Component
public class AdminRateLimitFilter extends OncePerRequestFilter {

    private static final String ANONYMOUS = "anonymous";

    private final AdminRateLimiter limiter;
    private final ObjectMapper objectMapper;

    public AdminRateLimitFilter(AdminRateLimiter limiter, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        // PR review I6:路径判定集中到 AdminPathMatcher,与 JwtAuthenticationFilter
        // 共用 — 避免两处独立实现不同步导致 bypass。
        return !AdminPathMatcher.isAdminPath(req.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String key = clientIp(req) + ":" + currentAccount();
        AdminRateLimiter.Decision d = limiter.tryAcquire(key);
        if (d.permitted()) {
            chain.doFilter(req, res);
            return;
        }
        writeRateLimitedResponse(res, d.retryAfterSeconds());
    }

    private void writeRateLimitedResponse(HttpServletResponse res, int retryAfter) throws IOException {
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
                "RATE_LIMITED",
                "Rate limit exceeded; retry after " + retryAfter + "s",
                null);
        objectMapper.writeValue(res.getOutputStream(), body);
    }

    private static String clientIp(HttpServletRequest req) {
        // 关键:只用 TCP 层对端 IP,绝不读 X-Forwarded-For 头 —— 否则攻击者
        // 任何请求都自带 XFF 即可绕过按 IP 的限流桶。生产环境由前置 nginx/ALB
        // 把真实客户端 IP 透到 TCP socket。
        String remote = req.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private static String currentAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return ANONYMOUS;
        }
        return auth.getName();
    }
}
