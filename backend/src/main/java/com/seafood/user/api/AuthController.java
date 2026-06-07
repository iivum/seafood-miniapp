package com.seafood.user.api;

import com.seafood.user.api.dto.RefreshRequest;
import com.seafood.user.api.dto.TokenResponse;
import com.seafood.user.api.dto.WechatLoginRequest;
import com.seafood.user.application.AuthService;
import com.seafood.user.application.TokenRevocationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.UserPrincipal;

import java.time.Instant;

/**
 * 小程序入口 — 微信 code 登录 + refresh + logout。
 *
 * <p>Sprint 2 §3.6 — {@code POST /api/auth/logout} 走两阶段:
 * <ol>
 *   <li>从 {@code Authorization} header 解出 jti + exp(用现有 {@link JwtTokenProvider})</li>
 *   <li>调 {@link TokenRevocationService#revoke(String, String, Instant)}</li>
 *   <li>返 204 No Content</li>
 * </ol>
 *
 * <p>为什么不在 controller 抛 401:filter 已把 token 验过,所以 SecurityContext 一定有
 * UserPrincipal;直接取其 id 即可。考虑到 RFC 6750 客户端可能在 logout 前 token 已
 * 经过期,filter 不会写 SecurityContext,这种情况返 401(未登录)。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER = "Bearer ";

    private final AuthService auth;
    private final JwtTokenProvider tokens;
    private final TokenRevocationService revocations;

    public AuthController(AuthService auth,
                          JwtTokenProvider tokens,
                          TokenRevocationService revocations) {
        this.auth = auth;
        this.tokens = tokens;
        this.revocations = revocations;
    }

    @PostMapping("/wechat-login")
    public TokenResponse wechatLogin(@Valid @RequestBody WechatLoginRequest req,
                                     HttpServletRequest httpReq) {
        // PR review #7:lockout key 必须用 clientIp(在 exchange 之前没有 openId)。
        // 不用 X-Forwarded-For — 由前置 nginx/ALB 写到 TCP socket,getRemoteAddr 已可信
        // (参见 AdminRateLimitFilter 同款决策)。
        return auth.wechatLogin(req, httpReq.getRemoteAddr());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req.refreshToken(), AuthService.Audience.USER);
    }

    /**
     * 登出当前 access token(Sprint 2 §3.6)。
     *
     * <p>从 Authorization header 解 jti + exp(签名/exp 校验仍走 JwtTokenProvider
     * 保证 token 没被伪造),写入 revoked_tokens,返 204。
     *
     * <p>PR review I3:删 {@code @PreAuthorize("isAuthenticated()")} ——
     * 过期 token 在 {@link JwtAuthenticationFilter} 不会写 SecurityContext,
     * 走到 controller 时 {@code me == null},{@code @PreAuthorize} 失败走
     * Spring 默认 {@code AccessDeniedHandler} 返 403,与本方法注释承诺的 401 不符。
     * 现:自己判 header 缺失/格式错 → 401;expired token → 仍 204(idempotent);
     * 签名错 / 解析失败 → 401(无法信任 jti)。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req,
                                       @AuthenticationPrincipal UserPrincipal me) {
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            return ResponseEntity.status(401).build();
        }
        String token = header.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        try {
            Claims claims = tokens.parseUser(token);
            String jti = claims.getId();
            Instant exp = claims.getExpiration() == null ? null : claims.getExpiration().toInstant();
            String userId = me == null ? claims.getSubject() : me.getId();
            revocations.revoke(jti, userId, exp);
        } catch (ExpiredJwtException e) {
            // 已过期:仍 204 — logout 幂等,不应当作错误(force-revoke 一个已死的 jti 没意义)
        } catch (JwtException e) {
            // 签名错 / 格式错:401(无法信任 jti,不能调 revoke)
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.noContent().build();
    }
}
