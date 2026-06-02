package com.seafood.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties props;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(String userId, String username, Role role) {
        return issue(userId, username, role, TOKEN_TYPE_ACCESS, Duration.ofMinutes(props.accessTokenTtlMinutes()));
    }

    public String issueRefreshToken(String userId, Role role) {
        return issue(userId, null, role, TOKEN_TYPE_REFRESH, Duration.ofDays(props.refreshTokenTtlDays()));
    }

    private String issue(String userId, String username, Role role, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId)
            .claim("role", role.name())
            .claim("username", username)
            .claim("type", type)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key)
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public Claims parseAccessToken(String token) {
        Claims claims = parse(token);
        if (!TOKEN_TYPE_ACCESS.equals(claims.get("type", String.class))) {
            throw new JwtException("Not an access token");
        }
        return claims;
    }

    public Claims parseRefreshToken(String token) {
        Claims claims = parse(token);
        if (!TOKEN_TYPE_REFRESH.equals(claims.get("type", String.class))) {
            throw new JwtException("Not a refresh token");
        }
        return claims;
    }
}
