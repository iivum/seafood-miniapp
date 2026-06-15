package com.seafood.user.application;

import com.seafood.user.infra.LoginAttemptDocument;
import com.seafood.user.infra.LoginAttemptRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * sprint-1-closure 2.3 — Admin 登录锁定服务(IP + account 双维度,3/15min)。
 *
 * <p>决策(见 design.md D3):
 * <ul>
 *   <li>单 IP 锁会被 NAT 误伤,单 account 锁会被攻击者换 IP 暴力破解;两个都计,任一维度
 *       超阈值都锁</li>
 *   <li>数据用 {@code login_attempts} MongoDB collection + TTL index(2.1)— 不上 Redis</li>
 *   <li>{@code getLockoutState(ip, account)} 是只读查询,供 admin UI 启动期轮询用</li>
 * </ul>
 *
 * <p>计数语义:
 * <ul>
 *   <li>{@code recordFailure}:写一条 {@code success=false} 记录到 login_attempts,触发 Micrometer 计数</li>
 *   <li>{@code recordSuccess}:写一条 {@code success=true} 记录 + 重置失败次数(TTL 自动清掉旧的)</li>
 *   <li>{@code isIpLocked / isAccountLocked}:过去 15 分钟内失败 ≥ 3 视为锁</li>
 * </ul>
 */
@Service
public class LoginLockoutService {

    /** 失败次数阈值 */
    public static final int FAILURE_THRESHOLD = 3;
    /** 锁定时长 15 分钟(秒) */
    public static final long LOCKOUT_DURATION_SECONDS = 900L;
    /** 滚动窗口 15 分钟 */
    public static final long WINDOW_SECONDS = 900L;

    private final LoginAttemptRepository repo;
    private final MeterRegistry meterRegistry;

    public LoginLockoutService(LoginAttemptRepository repo, MeterRegistry meterRegistry) {
        this.repo = repo;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记一次失败。如果本次失败把 IP 或 account 推到了 lock 阈值,
     * 返回 {@code true} 让 caller 可以立即 throw AccountLockedException,
     * 避免下一轮"先 isXLocked 后 recordFailure"的 read-after-write 竞态
     * (那个竞态会让第 4 次失败仍走 catch 而不是 423)。
     */
    public boolean recordFailure(String ip, String account) {
        repo.save(new LoginAttemptDocument(ip, account, false, Instant.now()));
        if (isIpLocked(ip) || isAccountLocked(account)) {
            meterRegistry.counter("users.login.attempts", "result", "locked").increment();
            return true;
        }
        return false;
    }

    public void recordSuccess(String ip, String account) {
        // 成功登录不需要把旧失败清掉 — TTL index 900s 后自动清;期间 isXLocked 会继续返 true
        // 直到窗口滑出。Sprint 4 follow-up:成功时主动删除该 ip/account 的失败记录,立即解锁。
        repo.save(new LoginAttemptDocument(ip, account, true, Instant.now()));
    }

    public boolean isIpLocked(String ip) {
        return countFailuresSince(ip, null) >= FAILURE_THRESHOLD;
    }

    public boolean isAccountLocked(String account) {
        return countFailuresSince(null, account) >= FAILURE_THRESHOLD;
    }

    /** 滚动窗口内失败次数(任一维度) */
    private long countFailuresSince(String ip, String account) {
        Instant since = Instant.now().minus(WINDOW_SECONDS, ChronoUnit.SECONDS);
        long count = 0;
        if (ip != null) {
            count = repo.countByIpAndSuccessAndTsAfter(ip, false, since);
        } else if (account != null) {
            count = repo.countByAccountAndSuccessAndTsAfter(account, false, since);
        }
        return count;
    }

    /**
     * 计算锁状态(供 GET /api/auth/login-lock 用)。
     * 优先级:IP 锁 > Account 锁 > NONE。
     */
    public LockoutState getLockoutState(String ip, String account) {
        Instant since = Instant.now().minus(WINDOW_SECONDS, ChronoUnit.SECONDS);
        if (ip != null && isIpLocked(ip)) {
            Instant until = earliestLockUntil(repo.findByIpAndTsAfterOrderByTsDesc(ip, since));
            return new LockoutState(true, until, "IP");
        }
        if (account != null && isAccountLocked(account)) {
            Instant until = earliestLockUntil(repo.findByAccountAndTsAfterOrderByTsDesc(account, since));
            return new LockoutState(true, until, "ACCOUNT");
        }
        return new LockoutState(false, null, "NONE");
    }

    private Instant earliestLockUntil(List<LoginAttemptDocument> records) {
        // 锁定直到第 (threshold) 次失败 + 15 分钟
        if (records.size() < FAILURE_THRESHOLD) return null;
        // 倒序拿第 N 条 = 第 (threshold) 个失败
        Instant trigger = records.get(FAILURE_THRESHOLD - 1).getTs();
        return trigger.plusSeconds(LOCKOUT_DURATION_SECONDS);
    }

    public record LockoutState(boolean locked, Instant until, String scope) {}
}
