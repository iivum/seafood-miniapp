package com.seafood.shared.security;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * 单次使用 refresh token 的消费记录(参见 design.md §4.1,specs/auth §Token refresh)。
 *
 * <p>字段:
 * <ul>
 *   <li>{@code jti} — refresh token 的唯一 id</li>
 *   <li>{@code userId} — 主体</li>
 *   <li>{@code familyId} — 登录族;任一 jti 被重用 → 整族撤销</li>
 *   <li>{@code audience} — {@code "user"} 或 {@code "admin"},防止跨域误用</li>
 *   <li>{@code consumed} — true 表示已使用,后续调用视为重用</li>
 *   <li>{@code expiresAt} — 用于后台清理</li>
 * </ul>
 */
@Document(collection = "refresh_tokens")
public class RefreshTokenRecord {

    @Id
    private String jti;

    @Indexed
    private String userId;

    @Indexed
    private String familyId;

    private String audience;
    private boolean consumed;
    private Instant expiresAt;

    public String getJti() { return jti; }
    public void setJti(String jti) { this.jti = jti; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getFamilyId() { return familyId; }
    public void setFamilyId(String familyId) { this.familyId = familyId; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public boolean isConsumed() { return consumed; }
    public void setConsumed(boolean consumed) { this.consumed = consumed; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
