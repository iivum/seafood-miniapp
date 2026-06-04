package com.seafood.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

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
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private final JwtTokenProvider tokens;

    public JwtAuthenticationFilter(JwtTokenProvider tokens) {
        this.tokens = tokens;
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

        try {
            Claims claims = isAdminPath(req.getRequestURI())
                    ? tokens.parseAdmin(token)
                    : tokens.parseUser(token);
            String userId = claims.getSubject();
            String roleStr = claims.get("role", String.class);
            if (userId == null || roleStr == null) {
                chain.doFilter(req, res);
                return;
            }
            Role role = Role.valueOf(roleStr);
            UserPrincipal principal = new UserPrincipal(userId, role);
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException e) {
            // 无效 token:不留 SecurityContext,授权层会拒绝
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(req, res);
    }

    private static boolean isAdminPath(String uri) {
        return uri != null && (uri.startsWith("/api/admin/") || uri.equals("/api/admin"));
    }
}
