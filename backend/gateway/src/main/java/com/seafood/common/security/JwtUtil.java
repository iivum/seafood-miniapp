package com.seafood.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    private final JwtProperties jwtProperties;
    private final SecretKey key;

    @Autowired
    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // 从配置中获取密钥，如果没有则生成新的密钥
        String secret = jwtProperties.getSecret();
        if (secret != null && !secret.isEmpty()) {
            // 使用Base64解码的密钥
            byte[] keyBytes = java.util.Base64.getDecoder().decode(secret);
            this.key = Keys.hmacShaKeyFor(keyBytes);
        } else {
            // 生成新的密钥（仅用于开发环境）
            this.key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        }
    }

    /**
     * 生成访问令牌
     * @param userId 用户ID
     * @param role 用户角色
     * @return JWT令牌
     */
    public String generateToken(String userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        return createToken(claims, userId, jwtProperties.getExpiration());
    }

    /**
     * 生成刷新令牌
     * @param userId 用户ID
     * @return 刷新令牌
     */
    public String generateRefreshToken(String userId) {
        return createToken(new HashMap<>(), userId, jwtProperties.getRefreshExpiration());
    }

    private String createToken(Map<String, Object> claims, String subject, long expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .issuer(jwtProperties.getIssuer())
                .signWith(key)
                .compact();
    }

    /**
     * 验证令牌
     * @param token JWT令牌
     * @param userId 用户ID
     * @return 是否有效
     */
    public Boolean validateToken(String token, String userId) {
        try {
            final String extractedUserId = extractUserId(token);
            return extractedUserId.equals(userId) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证令牌（使用UserDetails）
     * @param token JWT令牌
     * @param userDetails 用户详情
     * @return 是否有效
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        return validateToken(token, userDetails.getUsername());
    }

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    /**
     * 检查令牌是否即将过期（用于自动刷新）
     * @param token JWT令牌
     * @param threshold 阈值（毫秒）
     * @return 是否即将过期
     */
    public Boolean isTokenExpiringSoon(String token, long threshold) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        Date now = new Date();
        return expiration.getTime() - now.getTime() <= threshold;
    }

    /**
     * 获取令牌签发时间
     * @param token JWT令牌
     * @return 签发时间
     */
    public Date getIssuedAt(String token) {
        return extractClaim(token, Claims::getIssuedAt);
    }
}
