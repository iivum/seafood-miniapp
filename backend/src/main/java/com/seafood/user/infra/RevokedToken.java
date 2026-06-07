package com.seafood.user.infra;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * 撤销的 access token 记录(参见 design.md §3,specs/auth §Token revocation)。
 *
 * <p>字段:
 * <ul>
 *   <li>{@code _id = jti} — JWT 唯一 id,既是 MongoDB 主键也是 Caffeine cache 的 key</li>
 *   <li>{@code userId} — 主体,供 admin 端点审计 / force-logout 使用</li>
 *   <li>{@code expiresAt} — 与原 token 的 exp 一致;MongoDB TTL 索引
 *       ({@code { expiresAt: 1 }, expireAfterSeconds: 0}) 会在到期后自动删
 *       除,避免无限堆积(design §3 决策 3)</li>
 *   <li>{@code revokedAt} — 服务端记录时间,用于审计</li>
 * </ul>
 *
 * <p>为什么用 jti 自身作主键:访问模式是"按 jti 查存在性",{@code _id} 索引就是
 * 主键索引,1 次 lookup 即命中,无需再单独建 unique index。
 */
@Document(collection = "revoked_tokens")
public class RevokedToken {

    @Id
    private String jti;

    @Field("userId")
    private String userId;

    private Instant expiresAt;
    private Instant revokedAt;

    public RevokedToken() {
    }

    public RevokedToken(String jti, String userId, Instant expiresAt) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.revokedAt = Instant.now();
    }

    public String getJti() { return jti; }
    public void setJti(String jti) { this.jti = jti; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
