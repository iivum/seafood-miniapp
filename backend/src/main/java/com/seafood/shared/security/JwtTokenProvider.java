package com.seafood.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发/校验(参见 design.md §4.1,specs/auth §JWT issuance)。
 *
 * <p>失败模式:
 * <ul>
 *   <li>启动时 {@code secret}/{@code adminSecret} 缺失 → 抛 IllegalStateException,进程退出</li>
 *   <li>密钥长度 < 32 字节(HS256 要求) → 抛 IllegalStateException</li>
 *   <li>解析失败 → 抛 {@link JwtException},由 {@link JwtAuthenticationFilter} 翻译为 401</li>
 * </ul>
 */
@Component
public class JwtTokenProvider {

    private final JwtProperties props;
    private SecretKey userKey;
    private SecretKey adminKey;

    public JwtTokenProvider(JwtProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        this.userKey = buildKey("JWT_SECRET", props.getSecret());
        this.adminKey = buildKey("JWT_ADMIN_SECRET", props.getAdminSecret());
    }

    private static SecretKey buildKey(String envName, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(envName + " is missing; set env var or security.jwt.* property to enable auth");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(envName + " must be at least 32 bytes for HS256");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    // ----- 小程序 /api/auth/** -----

    /** 签发 access token(sub=userId, role, jti, iat, exp)。 */
    public IssuedToken issueAccessToken(String userId, Role role) {
        return issue(userKey, userId, role, props.getAccessTokenTtl(), "access");
    }

    /** 签发 refresh token(sub=userId, type=refresh, jti, iat, exp)。 */
    public IssuedToken issueRefreshToken(String userId) {
        return issue(userKey, userId, null, props.getRefreshTokenTtl(), "refresh");
    }

    // ----- admin-ui /api/admin/auth/**(独立密钥)-----

    public IssuedToken issueAdminAccessToken(String userId, Role role) {
        return issue(adminKey, userId, role, props.getAccessTokenTtl(), "access");
    }

    public IssuedToken issueAdminRefreshToken(String userId) {
        return issue(adminKey, userId, null, props.getRefreshTokenTtl(), "refresh");
    }

    // ----- 解析 -----

    public Claims parseUser(String token) {
        return Jwts.parser().verifyWith(userKey).build().parseSignedClaims(token).getPayload();
    }

    public Claims parseAdmin(String token) {
        return Jwts.parser().verifyWith(adminKey).build().parseSignedClaims(token).getPayload();
    }

    private IssuedToken issue(SecretKey key, String subject, Role role, Duration ttl, String type) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        String jti = UUID.randomUUID().toString();
        var builder = Jwts.builder()
                .id(jti)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("type", type);
        if (role != null) {
            builder.claim("role", role.name());
        }
        String token = builder.signWith(key).compact();
        return new IssuedToken(token, jti, now, exp);
    }

    /** 解析失败时区分过期/无效/签名错。 */
    public static String classify(Throwable t) {
        if (t instanceof io.jsonwebtoken.ExpiredJwtException) return "TOKEN_EXPIRED";
        if (t instanceof JwtException) return "TOKEN_INVALID";
        return "TOKEN_INVALID";
    }

    public record IssuedToken(String token, String jti, Instant issuedAt, Instant expiresAt) {
    }
}
