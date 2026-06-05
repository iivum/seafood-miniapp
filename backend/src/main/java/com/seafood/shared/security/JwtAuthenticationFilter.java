package com.seafood.shared.security;

import com.seafood.shared.error.ErrorResponse;
import com.seafood.user.application.TokenRevocationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 解析 Authorization: Bearer &lt;token&gt;,按 URL 前缀选 user / admin 密钥,把 UserPrincipal 写入 SecurityContext。
 *
 * <p>本 filter 只做"有 token 就解析",不做"无 token 就 401":
 * <ul>
 *   <li>无 token → 放行,SecurityConfig + @PreAuthorize 决定 401/403</li>
 *   <li>有 token 但无效 → 清除 SecurityContext 后放行(同样由后续层拒绝)</li>
 * </ul>
 * 这样公共读端点(/api/products 等)匿名可访问,写端点和管理端点由授权层拦截。
 *
 * <p>Sprint 2 §3.5 — 解析成功后追加撤销检查:若 {@code jti} 在
 * {@link TokenRevocationService#isRevoked(String, String)} 中命中,直接写
 * HTTP 401 + {@code code=TOKEN_REVOKED} body,<em>不</em>再走下游 ——
 * revoked token 一律就地拒绝,即便签名 + exp 都有效。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private final JwtTokenProvider tokens;
    private final TokenRevocationService revocations;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenProvider tokens,
                                   TokenRevocationService revocations,
                                   ObjectMapper objectMapper) {
        this.tokens = tokens;
        this.revocations = revocations;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(req, res);
            return;
        }
        String token = header.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            chain.doFilter(req, res);
            return;
        }

        Claims claims;
        try {
            claims = isAdminPath(req.getRequestURI())
                    ? tokens.parseAdmin(token)
                    : tokens.parseUser(token);
        } catch (JwtException e) {
            // 无效 token:不留 SecurityContext,授权层会拒绝
            SecurityContextHolder.clearContext();
            chain.doFilter(req, res);
            return;
        }

        String userId = claims.getSubject();
        String roleStr = claims.get("role", String.class);
        if (userId == null || roleStr == null) {
            chain.doFilter(req, res);
            return;
        }

        // Sprint 2 §3.5 — 撤销检查
        String jti = claims.getId();
        if (jti != null && revocations.isRevoked(jti, userId)) {
            writeRevoked(res);
            return;
        }

        Role role = Role.valueOf(roleStr);
        UserPrincipal principal = new UserPrincipal(userId, role);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(req, res);
    }

    private static boolean isAdminPath(String uri) {
        return uri != null && (uri.startsWith("/api/admin/") || uri.equals("/api/admin"));
    }

    /** 撤销 token 的 401 响应;与 {@link AdminRateLimitFilter} 同样的"filter 内直写"模式。 */
    private void writeRevoked(HttpServletResponse res) throws IOException {
        res.setStatus(HttpStatus.UNAUTHORIZED.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
                "TOKEN_REVOKED",
                "Token has been revoked; please log in again",
                null);
        objectMapper.writeValue(res.getOutputStream(), body);
    }
}
