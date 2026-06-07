package com.seafood.user.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.seafood.user.infra.RevokedToken;
import com.seafood.user.infra.RevokedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Token 撤销服务(Sprint 2 §3.4,specs/auth §Token revocation,design §3 decision 3)。
 *
 * <p>职责:
 * <ul>
 *   <li>{@link #revoke(String, String, Instant)} — 单 jti 撤销(对应 logout)</li>
 *   <li>{@link #revokeAllForUser(String)} — 把整个 user 的所有 jti 标记为已撤销
 *       (对应 admin force-logout;由于 active jti 不可枚举 — 找不到地方存"我发过
 *       的所有 jti",用通配符 sentinel 文档,见下)</li>
 *   <li>{@link #isRevoked(String, String)} — 高频读路径;先 Caffeine 再 MongoDB</li>
 * </ul>
 *
 * <p><b>缓存策略</b>(design §3 decision 3):双 TTL Caffeine:
 * <ul>
 *   <li>positive TTL(已撤销)60s:logout 后 60s 内 JwtAuthenticationFilter 直接命中缓存,
 *       0 次 Mongo lookup</li>
 *   <li>negative TTL(未撤销)5s:防止同一 jti 在 5s 内反复穿透到 Mongo,吸收突发流量
 *       (例如前端轮询)</li>
 * </ul>
 *
 * <p><b>force-logout 实现</b>(PR review #26):在 {@code revoked_tokens} 集合里写一条
 * {@code _id = "u:<userId>"} 的 sentinel 文档(实际常量 {@link #FORCE_LOGOUT_PREFIX} = {@code "u:"});
 * {@link #isRevoked(String, String)} 检测到这一前缀时,即使 jti 不在显式列表也返回 true。
 * 这种设计的代价是 1 个 Mongo 文档永远存活(TTL 不会过期它);但 {@code revoke} 时设很远的
 * {@code expiresAt},配合定期清理 job(本次不实现)即可。
 */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    /** 前缀 sentinel;同一前缀下所有真实 jti 视为被 force-logout。 */
    static final String FORCE_LOGOUT_PREFIX = "u:";

    /**
     * PR review I8:user-scope force-logout 缓存 key 前缀。
     * 该 key 在缓存中 value=TRUE 表示"该 user 已被 force-logout,任意 jti 都视为撤销"。
     * 用<em>独立</em>key(而非清全缓存),把 force-logout 失效范围缩小到单 user。
     */
    static final String FORCE_CACHE_KEY_PREFIX = "force:";

    private static final Duration POSITIVE_TTL = Duration.ofSeconds(60);
    private static final Duration NEGATIVE_TTL = Duration.ofSeconds(5);

    private final RevokedTokenRepository repo;
    private final Cache<String, Boolean> cache;
    private final Ticker ticker;

    /**
     * CI 修复:同 {@link com.seafood.shared.security.AdminRateLimiter} ——
     * 两个 constructor 触发 Spring 6 AOT 失败。{@code @Autowired} 显式标记 1-arg 为首选。
     */
    @Autowired
    public TokenRevocationService(RevokedTokenRepository repo) {
        this(repo, Ticker.systemTicker());
    }

    /** 测试用:注入假 {@link Ticker}。 */
    TokenRevocationService(RevokedTokenRepository repo, Ticker ticker) {
        this.repo = repo;
        this.ticker = ticker;
        this.cache = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfter(new com.github.benmanes.caffeine.cache.Expiry<String, Boolean>() {
                    @Override
                    public long expireAfterCreate(String key, Boolean value, long currentTime) {
                        Duration d = Boolean.TRUE.equals(value) ? POSITIVE_TTL : NEGATIVE_TTL;
                        return d.toNanos();
                    }
                    @Override
                    public long expireAfterUpdate(String key, Boolean value, long currentTime, long currentDuration) {
                        return expireAfterCreate(key, value, currentTime);
                    }
                    @Override
                    public long expireAfterRead(String key, Boolean value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    /** 撤销单个 jti。logout 用。 */
    public void revoke(String jti, String userId, Instant expiresAt) {
        if (jti == null || jti.isBlank()) return;
        // already-expired token 没必要写库:filter 看到 exp 早过就会拒绝(签名/exp 校验先行)
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            log.debug("[revoke] skip already-expired jti={} exp={}", jti, expiresAt);
            return;
        }
        RevokedToken rec = new RevokedToken(jti, userId, expiresAt);
        repo.save(rec);
        cache.put(jti, Boolean.TRUE);
    }

    /**
     * Force-logout:写一个 sentinel 文档使该 user 的所有 jti 被视为已撤销。
     *
     * <p>{@code _id} 形如 {@code u:USER_ID};{@link #isRevoked} 检测到这一前缀
     * 时即返回 true。{@code expiresAt} 设为 far-future(100 年),实践中依赖
     * 数据库 ops 清理(本服务不主动删)。
     */
    public void revokeAllForUser(String userId) {
        if (userId == null || userId.isBlank()) return;
        String sentinelId = FORCE_LOGOUT_PREFIX + userId;
        Instant farFuture = Instant.now().plus(Duration.ofDays(365L * 100L));
        RevokedToken rec = new RevokedToken(sentinelId, userId, farFuture);
        repo.save(rec);
        // PR review I8:不再 {@code cache.invalidateAll()} ——
        // 原实现清空整个 Caffeine,意味着 force-logout 一来,所有 user 的所有
        // jti 缓存都失效,下一个 5s 内 N 个并发 isRevoked 全部打 Mongo,
        // 形成 stampede(实测 QPS 高的 service 一次能打 5k+ Mongo lookup)。
        //
        // 现:只 invalidate 与本 user <em>直接相关</em>的缓存。
        // 具体策略:
        //   1. 写一个 user-scope 的 short TTL(<em>负</em>含义)的 force-cache 条目
        //      (key = "force:" + userId, value = TRUE)。
        //   2. isRevoked 检查这个 cache,若 TRUE 直接返,不必每次查 Mongo。
        //   3. revokeAll 写 sentinel + 写"force:<userId>=TRUE",仅本 user 的
        //      后续 isRevoked 命中此 cache;jti-cache 留给 5s TTL 自然过期。
        cache.put(FORCE_CACHE_KEY_PREFIX + userId, Boolean.TRUE);
    }

    /**
     * 检查 jti 是否已撤销。
     *
     * <p>实现:
     * <ol>
     *   <li>查 Caffeine;命中即返回</li>
     *   <li>未命中 → 查 MongoDB:存在文档 → true;否则 false</li>
     *   <li>同时查 user sentinel;若 userId 提供,匹配则 true</li>
     * </ol>
     *
     * @param jti    JWT id(从 claims.getId() 取)
     * @param userId 可选 — 传 null 时跳过 sentinel 检查(性能更快,filter 已知 userId)
     */
    public boolean isRevoked(String jti, String userId) {
        if (jti == null || jti.isBlank()) return false;
        // PR review I8:先查 user-scope force-cache —— 若有 TRUE,直接返
        // (不走 Mongo,jti-cache 也不动)。这把 force-logout 失效范围压到单 user,
        // 避免 cache.invalidateAll() 引发全 QPS stampede。
        if (userId != null) {
            Boolean forceCached = cache.getIfPresent(FORCE_CACHE_KEY_PREFIX + userId);
            if (Boolean.TRUE.equals(forceCached)) {
                return true;
            }
        }
        Boolean cached = cache.getIfPresent(jti);
        if (Boolean.TRUE.equals(cached)) {
            return true;
        }
        if (Boolean.FALSE.equals(cached)) {
            return false;
        }
        boolean inDb = repo.existsById(jti);
        if (inDb) {
            cache.put(jti, Boolean.TRUE);
            return true;
        }
        if (userId != null) {
            // Mongo 上的 sentinel 仅在 force-cache miss 时查 ——
            // force-cache hit 已直接走 TRUE 短路,不会到这里。
            boolean force = repo.existsById(FORCE_LOGOUT_PREFIX + userId);
            if (force) {
                // 顺手把 force-cache 也填上,后续同 user 请求 0 Mongo
                cache.put(FORCE_CACHE_KEY_PREFIX + userId, Boolean.TRUE);
                cache.put(jti, Boolean.TRUE);
                return true;
            }
        }
        // PR review #22 — 关键:用 putIfAbsent 写 FALSE。如果并发 {@link #revoke} 在
        // 我们 DB 读与缓存写之间抢先 put 了 TRUE,我们的 FALSE 不会覆盖它。
        // 原实现用 {@code cache.put(jti, FALSE)} 是 last-write-wins,存在窗口:
        //   T1 isRevoked → cache miss → DB 读 false
        //   T2 revoke     → DB save + cache.put(TRUE)
        //   T1            → cache.put(FALSE) 覆盖 TRUE → 后续 5s 内 isRevoked 都返 false(错)
        // putIfAbsent 让 TRUE 永远赢。
        Boolean prev = cache.asMap().putIfAbsent(jti, Boolean.FALSE);
        if (Boolean.TRUE.equals(prev)) {
            // 并发 revoke 在我们读 DB 期间抢先 put 了 TRUE;以 TRUE 为准
            return true;
        }
        return false;
    }

    /** 暴露给 controller / service 内部用:把 jti 列表展开(便于测试断言)。 */
    public List<RevokedToken> findRevokedByUser(String userId) {
        return repo.findByUserId(userId);
    }

    /** 测试/管理用:清空缓存(不删 Mongo 文档)。 */
    public void invalidateCache() {
        cache.invalidateAll();
    }
}
