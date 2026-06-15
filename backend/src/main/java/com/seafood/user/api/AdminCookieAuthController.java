package com.seafood.user.api;

import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.user.api.dto.AdminLoginRequest;
import com.seafood.user.api.dto.TokenResponse;
import com.seafood.user.application.AuthService;
import com.seafood.user.application.LoginLockoutService;
import com.seafood.user.application.TokenRevocationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * Admin UI cookie-based 鉴权(路线图 2.12,specs/auth §Admin cookie-based authentication)。
 *
 * <p>三端点(全部独立签名密钥 {@code JWT_ADMIN_SECRET}):
 * <ul>
 *   <li>{@code POST /api/admin/auth/cookie-login} — 设 httpOnly cookie + 返 csrf token(204)</li>
 *   <li>{@code POST /api/admin/auth/logout} — 清 cookie + revoke 当前 jti(204)</li>
 *   <li>{@code GET  /api/admin/auth/csrf} — 返 csrf token(200,JSON)</li>
 * </ul>
 *
 * <p>Cookie 配置(决策 2):httpOnly + Secure(可关) + SameSite=Lax + Path=/ + Max-Age=900。
 * 系统 MUST NOT 接受 {@code Authorization: Bearer ...} 头部 — 仅 cookie;
 * 由 {@link com.seafood.shared.security.JwtAuthenticationFilter} 在 cookie 缺失时
 * 走 fallback 读 {@code Authorization}(Sprint 1 后会让 mp 路径走 fallback 而 admin 路径被
 * SecurityConfig 强制仅 cookie,本期暂保留 fallback 路径以兼容现有 admin 端点)。
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminCookieAuthController {

    private static final String ADMIN_COOKIE = "seafood_admin_token";
    private static final int COOKIE_MAX_AGE_SECONDS = 900; // 15 min
    private static final SecureRandom RNG = new SecureRandom();

    private final AuthService auth;
    private final TokenRevocationService revocation;
    private final JwtTokenProvider tokens;
    private final LoginLockoutService lockout;

    /** 部署在 HTTPS 时设 true;dev 本机 HTTP 下设 false 让浏览器保留 cookie。 */
    @Value("${admin.cookie.secure:false}")
    private boolean cookieSecure;

    public AdminCookieAuthController(AuthService auth,
                                     TokenRevocationService revocation,
                                     JwtTokenProvider tokens,
                                     LoginLockoutService lockout) {
        this.auth = auth;
        this.revocation = revocation;
        this.tokens = tokens;
        this.lockout = lockout;
    }

    // ----- POST /api/admin/auth/cookie-login -----

    /**
     * 登录并下发 httpOnly cookie。
     * <ul>
     *   <li>成功 → 204 No Content,响应头 {@code Set-Cookie: seafood_admin_token=...; HttpOnly; ...}</li>
     *   <li>失败 → {@code AuthService.adminLogin} 抛 {@code DomainException} /
     *       {@code AccountLockedException},由 {@code GlobalExceptionHandler} 翻译为 401/423</li>
     * </ul>
     * CSRF token 不放在 204 body(规范要求 body 为空),改由首次加载 dashboard 前
     * 调 {@code GET /api/admin/auth/csrf} 获取(详见 2.15 admin-ui 拦截器)。
     */
    @PostMapping("/cookie-login")
    public ResponseEntity<?> cookieLogin(@Valid @RequestBody AdminLoginRequest req,
                                            HttpServletRequest httpReq,
                                            HttpServletResponse httpRes) {
        String ip = httpReq.getRemoteAddr();

        // sprint-1-closure 2.4:IP 锁先于账户锁(防 admin 拿同一个错误密码扫 100 个账号)
        if (lockout.isIpLocked(ip)) {
            return ResponseEntity.status(429)
                    .header("Retry-After", "900")
                    .body(Map.of(
                            "code", "AUTH_LOCKED",
                            "message", "登录尝试次数过多,请 15 分钟后再试",
                            "retryAfterSeconds", 900));
        }
        if (lockout.isAccountLocked(req.username())) {
            return ResponseEntity.status(423)
                    .body(Map.of(
                            "code", "ACCOUNT_LOCKED",
                            "message", "账户已被锁定,请 15 分钟后再试",
                            "retryAfterSeconds", 900));
        }

        try {
            // 复用 AuthService.adminLogin 的凭据校验 + access/refresh 签发
            TokenResponse body = auth.adminLogin(req, ip);
            lockout.recordSuccess(ip, req.username());
            writeAdminCookie(httpRes, body.accessToken());
            return ResponseEntity.noContent().build();
        } catch (com.seafood.shared.error.DomainException e) {
            // 业务失败 → 记一次失败,可能触发 IP/account 锁
            lockout.recordFailure(ip, req.username());
            throw e;  // GlobalExceptionHandler 翻译 401/AccountLockedException
        }
    }

    /**
     * sprint-1-closure 2.6 — 查询锁状态(只读,公开)。前端在登录页 mount 时
     * 调一次,看是否需要进入倒计时。
     */
    @GetMapping("/login-lock")
    public Map<String, Object> loginLock(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String ip) {
        LoginLockoutService.LockoutState s = lockout.getLockoutState(ip, phone);
        return Map.of(
                "locked", s.locked(),
                "until", s.until() == null ? "" : s.until().toString(),
                "scope", s.scope());
    }

    // ----- POST /api/admin/auth/logout -----

    /**
     * 登出:从 cookie 读 access JWT,parse jti,revoke;清 cookie。
     * <p>无 cookie / cookie 失效也返 204(幂等)— 安全日志记录原因即可。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpReq, HttpServletResponse httpRes) {
        String token = readAdminCookie(httpReq);
        if (token != null) {
            try {
                Claims claims = tokens.parseAdmin(token);
                String jti = claims.getId();
                String userId = claims.getSubject();
                Instant exp = claims.getExpiration() == null ? null : claims.getExpiration().toInstant();
                if (jti != null) {
                    revocation.revoke(jti, userId, exp);
                }
            } catch (JwtException ignored) {
                // cookie 里的 token 已失效/伪造 — 不再 revoke,只清 cookie 让浏览器丢弃
            }
        }
        clearAdminCookie(httpRes);
        return ResponseEntity.noContent().build();
    }

    // ----- GET /api/admin/auth/csrf -----

    /**
     * 返 CSRF token 字符串(16 字节随机 hex),与当前会话 cookie 隐式绑定
     * (无状态,验证时由 {@code CsrfFilter} 校验 header 与 cookie/cookie-jti 哈希一致)。
     * <p>本期实现极简:返回新 token,无 server-side 存储,后续 2.13 升级为 HMAC(cookie-jti) 派生。
     */
    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf() {
        byte[] bytes = new byte[16];
        RNG.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        return ResponseEntity.ok(Map.of("csrfToken", token));
    }

    // ----- helpers -----

    private void writeAdminCookie(HttpServletResponse res, String token) {
        ResponseCookieBuilder b = ResponseCookieBuilder.start(ADMIN_COOKIE, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(COOKIE_MAX_AGE_SECONDS);
        res.addHeader(HttpHeaders.SET_COOKIE, b.build());
    }

    private void clearAdminCookie(HttpServletResponse res) {
        ResponseCookieBuilder b = ResponseCookieBuilder.start(ADMIN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0);
        res.addHeader(HttpHeaders.SET_COOKIE, b.build());
    }

    private String readAdminCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (ADMIN_COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    /**
     * 极简 Cookie 序列化器 — 避免引入 spring-boot-starter 的 ResponseCookie(本仓 4.0.6
     * 的 ResponseCookie 在 native-image 下有反射问题,见 PR review #15;
     * 手写 + path/httpOnly/secure/sameSite/maxAge 5 个 attr 已覆盖 spec 全部要求)。
     */
    private static final class ResponseCookieBuilder {
        private final String name;
        private final String value;
        private boolean httpOnly;
        private boolean secure;
        private String sameSite;
        private String path;
        private int maxAge;

        private ResponseCookieBuilder(String name, String value) {
            this.name = name;
            this.value = value;
        }

        static ResponseCookieBuilder start(String name, String value) {
            return new ResponseCookieBuilder(name, value);
        }

        ResponseCookieBuilder httpOnly(boolean v) { this.httpOnly = v; return this; }
        ResponseCookieBuilder secure(boolean v) { this.secure = v; return this; }
        ResponseCookieBuilder sameSite(String v) { this.sameSite = v; return this; }
        ResponseCookieBuilder path(String v) { this.path = v; return this; }
        ResponseCookieBuilder maxAge(int v) { this.maxAge = v; return this; }

        String build() {
            StringBuilder sb = new StringBuilder(name).append('=').append(value);
            if (path != null) sb.append("; Path=").append(path);
            if (httpOnly) sb.append("; HttpOnly");
            if (secure) sb.append("; Secure");
            if (sameSite != null) sb.append("; SameSite=").append(sameSite);
            if (maxAge > 0 || maxAge == 0) sb.append("; Max-Age=").append(maxAge);
            return sb.toString();
        }
    }
}
