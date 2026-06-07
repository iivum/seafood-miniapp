package com.seafood.user.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 登录失败锁配置(Sprint 2 §3.8,specs/auth §Login lockout,design §3 决策 3)。
 *
 * <p>三个数值默认取 OWASP 凭证填充建议:5 次失败 / 15 分钟窗口 / 锁 15 分钟。
 * 用户 / admin 共享同一组阈值,符合"对攻击者一视同仁"的安全模型。
 */
@ConfigurationProperties(prefix = "security.login-lock")
@Validated
public class LoginAttemptProperties {

    /** 触发锁定的连续失败次数。 */
    private int maxFailures = 5;

    /** 失败计数滚动窗口(分钟)。超出窗口的失败自动从 Caffeine 桶里被 evict。 */
    private int windowMinutes = 15;

    /** 锁定时长(分钟)。 */
    private int lockMinutes = 15;

    public int getMaxFailures() { return maxFailures; }
    public void setMaxFailures(int v) { this.maxFailures = v; }

    public int getWindowMinutes() { return windowMinutes; }
    public void setWindowMinutes(int v) { this.windowMinutes = v; }

    public int getLockMinutes() { return lockMinutes; }
    public void setLockMinutes(int v) { this.lockMinutes = v; }
}
