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
 * <p>作用域:仅匹配 {@code /api/admin/**} 的请求;其它路径短路直接放行(spec 场景
 * "Non-admin path not rate-limited")。
 *
 * <p>桶 key: {@code clientIp + ":" + account}。未鉴权请求用 anonymous 占位,
 * 这样未鉴权 brute-force 仍然受同一桶限制(只是单 IP 维度的 60 rpm)。
 *
 * <p>超限时<em>直接写 429 响应</em>(不走 GlobalExceptionHandler):Filter 抛出的
 * 异常不会经过 {@code @RestControllerAdvice},标准做法是 filter 自己负责序列化
 * {@link ErrorResponse} body。{@code GlobalExceptionHandler} 仍保留
 * {@code RateLimitedException} handler,用于将来 controller 直接抛该异常的场景。
 */
@Component
public class AdminRateLimitFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX = "/api/admin/";
    private static final String ANONYMOUS = "anonymous";

    private final AdminRateLimiter limiter;
    private final ObjectMapper objectMapper;

    public AdminRateLimitFilter(AdminRateLimiter limiter, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return uri == null || !uri.startsWith(ADMIN_PATH_PREFIX);
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
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma < 0 ? fwd : fwd.substring(0, comma)).trim();
        }
        return req.getRemoteAddr() == null ? "unknown" : req.getRemoteAddr();
    }

    private static String currentAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return ANONYMOUS;
        }
        return auth.getName();
    }
}
