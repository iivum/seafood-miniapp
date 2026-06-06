package com.seafood.user.application;

import com.seafood.shared.error.DomainException;
import com.seafood.shared.security.JwtTokenProvider;
import com.seafood.shared.security.RefreshTokenRecord;
import com.seafood.shared.security.RefreshTokenStore;
import com.seafood.shared.security.Role;
import com.seafood.user.api.dto.AdminLoginRequest;
import com.seafood.user.api.dto.TokenResponse;
import com.seafood.user.api.dto.WechatLoginRequest;
import com.seafood.user.infra.UserDocument;
import com.seafood.user.infra.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
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

    /**
     * Admin 启动期凭据 — 简化版,生产应替换为 {@code UserRepository.findByUsernameAndPasswordHash}
     * 走 DB 校验(参见 design §4 decision 5)。当前阶段(Sprint 2)用配置注入。
     *
     * <p><b>用户</b>默认 {@code admin}(非机密,可走默认)。<br>
     * <b>密码</b>无默认值,缺失即 fail-fast — {@code @Value} 不带 {@code :default} 时,
     * Spring 启动期解析失败会抛 {@code UnresolvedEmbeddedValueException} 直接阻断 ready。
     * 这是 PR review #8 / CLAUDE.md "禁硬编码密钥" 的硬要求:任何部署忘记设
     * {@code ADMIN_BOOTSTRAP_PASSWORD},启动就会失败,不可能以默认密码进入生产。
     */
    @Value("${admin.bootstrap.username:admin}")
    private String adminUsername;
    @Value("${admin.bootstrap.password}")
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

    /**
     * @param clientIp 调用方 IP(来自 {@code HttpServletRequest.getRemoteAddr()});
     *                 在 exchange 之前没有 openId,必须用 IP 作 lockout key(PR review #7)。
     *                 原实现用 {@code code.hashCode()},但 code 是微信一次性凭据,
     *                 每次请求新生成 → 不同 code 命中不同 key → 攻击者无限重试,
     *                 永远碰不到 {@code maxFailures} 阈值,完全绕过登录锁。
     */
    public TokenResponse wechatLogin(WechatLoginRequest req, String clientIp) {
        // 阶段 1:exchange 之前没有 openId,用 IP 锁 — 防止单 IP 不断换 code 暴力打
        String ipAccount = "wechat-ip:" + (clientIp == null || clientIp.isBlank() ? "unknown" : clientIp);
        ensureNotLocked(ipAccount);

        String openId;
        try {
            openId = wechat.exchange(req.code());
        } catch (RuntimeException e) {
            int lockRetry = loginAttempts.recordFailure(ipAccount);
            if (lockRetry > 0) {
                throw new AccountLockedException(lockRetry);
            }
            throw e;
        }

        // 阶段 2:exchange 成功,改用 openId 锁 — 防止定向爆破某一用户(openId 不变 → 计数累加)
        String openIdAccount = "wechat:" + openId;
        int lockedRetry = loginAttempts.isLocked(openIdAccount);
        if (lockedRetry > 0) {
            // 释放 IP 计数器(本次 IP 未失败);让用户的 openId 锁生效
            loginAttempts.recordSuccess(ipAccount);
            throw new AccountLockedException(lockedRetry);
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
            // 微信 code 命中管理员账号 → 拒绝(管理员必须走 admin 登录);用 openId 计数,
            // 攻击者反复换 code 不会绕过(只要目标 openId 锁定,就持续拒绝)
            int lockRetry = loginAttempts.recordFailure(openIdAccount);
            loginAttempts.recordSuccess(ipAccount); // IP 这边成功了
            if (lockRetry > 0) {
                throw new AccountLockedException(lockRetry);
            }
            throw new DomainException("该 openId 已绑定管理员账号,请使用管理员入口");
        }
        // 成功:两个计数器都清零(IP 这次没失败,openId 走通了)
        loginAttempts.recordSuccess(ipAccount);
        loginAttempts.recordSuccess(openIdAccount);
        return issuePair(doc.getId(), Role.CUSTOMER, Audience.USER);
    }

    // ----- Admin UI /api/admin/auth/login -----

    /**
     * @param clientIp 调用方 IP(来自 {@code HttpServletRequest.getRemoteAddr()});
     *                 用于 IP 维度的 lockout,防止攻击者以海量不同 username 撞出无界桶
     *                 撑爆 Caffeine(PR review push-sweep #4)。
     */
    public TokenResponse adminLogin(AdminLoginRequest req, String clientIp) {
        // PR review #8:空密码直接拒绝(覆盖 env 设为空串的场景)。
        // @Value 兜底"无默认"只保证"未设置"时 fail-fast;空串虽然能注入但实际不可用。
        if (adminPassword == null || adminPassword.isBlank()) {
            // 启动期本应就被 @ConfigurationProperties 校验拦住;此处是 double safety。
            throw new IllegalStateException(
                    "admin.bootstrap.password is not configured (set ADMIN_BOOTSTRAP_PASSWORD env). "
                            + "Application is not safe to start.");
        }
        // 阶段 1:用<em>规范化</em>的 username 锁 —— 只对配置里的 adminUsername 计数,
        // 其余全部归并到 {@code admin:unknown} 一个固定桶,避免 username 撞出无界桶。
        String normalizedUsername = adminUsername.equals(req.username()) ? adminUsername : "unknown";
        String account = "admin:" + normalizedUsername;
        ensureNotLocked(account);

        // 阶段 2:用 IP 锁 —— 同一 IP 多次失败触发锁定,与 username 正交。
        // 没有这一步的话,攻击者从多 IP 各猜 5 次,绕过 per-username 锁。
        String ipAccount = "admin-ip:" + (clientIp == null || clientIp.isBlank() ? "unknown" : clientIp);
        ensureNotLocked(ipAccount);

        boolean credsOk = adminUsername.equals(req.username()) && adminPassword.equals(req.password());
        if (!credsOk) {
            int lockRetry = loginAttempts.recordFailure(account);
            loginAttempts.recordFailure(ipAccount);
            if (lockRetry > 0) {
                // 这次失败触发了锁定 → 返 423 而不是 409
                throw new AccountLockedException(lockRetry);
            }
            throw new DomainException("用户名或密码错误");
        }
        // 凭据正确:两个计数器都清零
        loginAttempts.recordSuccess(account);
        loginAttempts.recordSuccess(ipAccount);
        // 简单起见:admin 凭据来自配置,不查库。生产应改为 UserRepository.findByUsernameAndPasswordHash
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
                ? tokens.issueAdminRefreshToken(userId, role)
                : tokens.issueRefreshToken(userId, role);

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
        // BUG fix (PR review #2 / silent-failure-hunter #1):原实现调用 findByFamilyId 两次,
        // 在第一份 list 上 setConsumed 后却 saveAll 第二份(fresh)list,导致变更被丢弃 —
        // TOKEN_REUSED 检测后 family 实际未被撤销,攻击者可继续 refresh。
        // 现:同一份 list mutate 后直接 saveAll。
        List<RefreshTokenRecord> records = refreshStore.findByFamilyId(familyId);
        for (RefreshTokenRecord r : records) {
            r.setConsumed(true);
        }
        refreshStore.saveAll(records);
    }

    public enum Audience { USER, ADMIN }
}
