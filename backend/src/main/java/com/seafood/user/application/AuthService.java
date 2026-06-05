package com.seafood.user.application;

import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.RefreshTokenRecord;
import com.seafood.shared.security.RefreshTokenStore;
import com.seafood.shared.security.Role;
import com.seafood.user.api.dto.AdminLoginRequest;
import com.seafood.user.api.dto.TokenResponse;
import com.seafood.user.api.dto.WechatLoginRequest;
import com.seafood.user.domain.User;
import com.seafood.user.infra.UserDocument;
import com.seafood.user.infra.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * 鉴权服务 — 微信小程序 / admin UI 双入口,共享单次 refresh 语义。
 *
 * <p>登录成功后:
 * <ol>
 *   <li>签发 access + refresh token</li>
 *   <li>创建 {@code familyId},把 refresh 的 jti 写入 {@link RefreshTokenRecord}</li>
 * </ol>
 * 刷新时:按 jti 查找记录,未消费 → 标记 consumed 并发新对;已消费 → 整族撤销。
 *
 * <p>Sprint 2 §3.11 — 所有 login 入口(wechat/admin)前置 {@link LoginAttemptService}
 * 锁检查;失败 → 累加;成功 → 清零。具体阈值由 {@code LoginAttemptProperties} 控制。
 */
@Service
public class AuthService {

    private final JwtTokenProvider tokens;
    private final RefreshTokenStore refreshStore;
    private final UserRepository users;
    private final WechatCodeExchanger wechat;
    private final LoginAttemptService loginAttempts;

    /** 开发期默认 admin 凭据;生产应替换为 DB 校验。 */
    @Value("${admin.bootstrap.username:admin}")
    private String adminUsername;
    @Value("${admin.bootstrap.password:admin123}")
    private String adminPassword;

    public AuthService(JwtTokenProvider tokens,
                       RefreshTokenStore refreshStore,
                       UserRepository users,
                       WechatCodeExchanger wechat,
                       LoginAttemptService loginAttempts) {
        this.tokens = tokens;
        this.refreshStore = refreshStore;
        this.users = users;
        this.wechat = wechat;
        this.loginAttempts = loginAttempts;
    }

    // ----- 小程序 /api/auth/wechat-login -----

    public TokenResponse wechatLogin(WechatLoginRequest req) {
        // 微信登录用 openId 作为 account key;前端 userId 不可得,先空预检;
        // 失败累计到 openId 上,这样同一个用户连续换 code 也会被锁。
        String account = "wechat:" + (req.code() == null ? "" : req.code().hashCode());
        ensureNotLocked(account);

        String openId;
        try {
            openId = wechat.exchange(req.code());
        } catch (RuntimeException e) {
            int lockRetry = loginAttempts.recordFailure(account);
            if (lockRetry > 0) {
                throw new AccountLockedException(lockRetry);
            }
            throw e;
        }

        UserDocument doc = users.findByOpenId(openId).orElseGet(() -> {
            UserDocument created = new UserDocument();
            created.setOpenId(openId);
            created.setNickname(req.nickname() == null ? "微信用户" : req.nickname());
            created.setAvatarUrl(req.avatarUrl());
            created.setRole(Role.CUSTOMER.name());
            created.setCreatedAt(Instant.now());
            return users.save(created);
        });
        if (!Role.CUSTOMER.name().equals(doc.getRole())) {
            // 微信 code 命中管理员账号 → 拒绝(管理员必须走 admin 登录)
            int lockRetry = loginAttempts.recordFailure(account);
            if (lockRetry > 0) {
                throw new AccountLockedException(lockRetry);
            }
            throw new DomainException("该 openId 已绑定管理员账号,请使用管理员入口");
        }
        loginAttempts.recordSuccess(account);
        return issuePair(doc.getId(), Role.CUSTOMER, Audience.USER);
    }

    // ----- Admin UI /api/admin/auth/login -----

    public TokenResponse adminLogin(AdminLoginRequest req) {
        String account = "admin:" + (req.username() == null ? "" : req.username());
        ensureNotLocked(account);

        if (!adminUsername.equals(req.username()) || !adminPassword.equals(req.password())) {
            int lockRetry = loginAttempts.recordFailure(account);
            if (lockRetry > 0) {
                // 这次失败触发了锁定 → 返 423 而不是 409
                throw new AccountLockedException(lockRetry);
            }
            throw new DomainException("用户名或密码错误");
        }
        // 简单起见:admin 凭据来自配置,不查库。生产应改为 UserRepository.findByUsernameAndPasswordHash
        loginAttempts.recordSuccess(account);
        return issuePair("admin-bootstrap", Role.ADMIN, Audience.ADMIN);
    }

    // ----- 通用 refresh(user + admin)-----

    public TokenResponse refresh(String refreshToken, Audience audience) {
        Claims claims;
        try {
            claims = audience == Audience.ADMIN ? tokens.parseAdmin(refreshToken) : tokens.parseUser(refreshToken);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new DomainException("TOKEN_EXPIRED");
        } catch (JwtException e) {
            throw new DomainException("TOKEN_INVALID");
        }
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new DomainException("TOKEN_INVALID");
        }
        String jti = claims.getId();
        String userId = claims.getSubject();

        RefreshTokenRecord rec = refreshStore.findByJti(jti)
                .orElseThrow(() -> new DomainException("TOKEN_INVALID"));

        if (rec.isConsumed()) {
            // 重用 → 撤销整族
            revokeFamily(rec.getFamilyId());
            throw new DomainException("TOKEN_REUSED");
        }
        if (rec.getExpiresAt() != null && rec.getExpiresAt().isBefore(Instant.now())) {
            throw new DomainException("TOKEN_EXPIRED");
        }
        if (!audience.name().equalsIgnoreCase(rec.getAudience())) {
            throw new DomainException("TOKEN_INVALID");
        }

        rec.setConsumed(true);
        refreshStore.save(rec);

        Role role = Role.valueOf(claims.get("role", String.class));
        return issuePair(userId, role, audience, rec.getFamilyId());
    }

    // ----- helpers -----

    private void ensureNotLocked(String account) {
        int retryAfter = loginAttempts.isLocked(account);
        if (retryAfter > 0) {
            throw new AccountLockedException(retryAfter);
        }
    }

    private TokenResponse issuePair(String userId, Role role, Audience audience) {
        return issuePair(userId, role, audience, UUID.randomUUID().toString());
    }

    private TokenResponse issuePair(String userId, Role role, Audience audience, String familyId) {
        JwtTokenProvider.IssuedToken access = audience == Audience.ADMIN
                ? tokens.issueAdminAccessToken(userId, role)
                : tokens.issueAccessToken(userId, role);
        JwtTokenProvider.IssuedToken refresh = audience == Audience.ADMIN
                ? tokens.issueAdminRefreshToken(userId)
                : tokens.issueRefreshToken(userId);

        RefreshTokenRecord rec = new RefreshTokenRecord();
        rec.setJti(refresh.jti());
        rec.setUserId(userId);
        rec.setFamilyId(familyId);
        rec.setAudience(audience.name());
        rec.setConsumed(false);
        rec.setExpiresAt(refresh.expiresAt());
        refreshStore.save(rec);

        return new TokenResponse(
                access.token(), refresh.token(),
                access.expiresAt(), refresh.expiresAt(),
                role.name());
    }

    private void revokeFamily(String familyId) {
        for (RefreshTokenRecord r : refreshStore.findByFamilyId(familyId)) {
            r.setConsumed(true);
        }
        refreshStore.saveAll(refreshStore.findByFamilyId(familyId));
    }

    public enum Audience { USER, ADMIN }
}
