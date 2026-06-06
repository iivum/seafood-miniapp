package com.seafood.shared.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Sprint 2 push-sweep #2 — {@code /actuator/health/mongoIndexes} 暴露关键索引状态。
 *
 * <p>背景:{@link MongoIndexInitializer#init()} 用
 * {@code @EventListener(ApplicationReadyEvent.class)} 启动,这是设计权衡:
 * <ul>
 *   <li>ready 事件后跑,意味着 critical 索引(TTL + openId unique)创建失败时,
 *       {@code /actuator/health} 仍可能短暂返 UP — 直到下一次 health probe</li>
 *   <li>抛 {@link IndexInitializationException} 只能进 ERROR 日志,不能阻止 ready</li>
 * </ul>
 *
 * <p>缓解:本 indicator 在每次 health probe 时<em>实时</em>检查关键索引是否存在,
 * 缺失立即报 DOWN,触发 Kubernetes readinessProbe 失败,Pod 不会被路由。
 * 这样即便 ready 时索引存在,后续被运维误删也能在秒级被探测到。
 *
 * <p>健康检查查询很轻量(只 listIndex 一次),不会影响生产吞吐。
 */
@Component("mongoIndexes")
public class MongoIndexHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexHealthIndicator.class);

    private final MongoTemplate mongo;
    /** 启动期 init 完成后写 true;若 init() 抛过或尚未跑完,health probe 报 UNKNOWN。 */
    private volatile boolean criticalIndexesEnsured = false;

    public MongoIndexHealthIndicator(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** 由 {@link MongoIndexInitializer#init()} 完成后调用,标记 ready。 */
    void onCriticalIndexesEnsured() {
        this.criticalIndexesEnsured = true;
    }

    @Override
    public Health health() {
        if (!criticalIndexesEnsured) {
            // 启动期 index init 还没跑完(还在 init 抛错,或 ApplicationReady 还没触发)。
            // 报 UNKNOWN — readiness 应等 init 完成,避免错把"启动中"标 UP。
            return Health.unknown()
                    .withDetail("reason", "MongoIndexInitializer has not completed; "
                            + "ApplicationReadyEvent not yet fired or init() failed")
                    .build();
        }
        // 实时 listIndex 验证 — 索引可能被运维误删
        boolean usersOk = hasIndex("users", "uk_openId");
        boolean revokedOk = hasIndex("revoked_tokens", "ttl_expiresAt");
        if (usersOk && revokedOk) {
            return Health.up()
                    .withDetail("users.uk_openId", "present")
                    .withDetail("revoked_tokens.ttl_expiresAt", "present")
                    .build();
        }
        return Health.down()
                .withDetail("users.uk_openId", usersOk ? "present" : "MISSING")
                .withDetail("revoked_tokens.ttl_expiresAt", revokedOk ? "present" : "MISSING")
                .withDetail("action", "critical index missing; "
                        + "revoked tokens may accumulate unbounded or openId duplicates allowed. "
                        + "Re-run MongoIndexInitializer or restore from backup.")
                .build();
    }

    private boolean hasIndex(String collection, String indexName) {
        try {
            return mongo.indexOps(collection).getIndexInfo().stream()
                    .anyMatch(i -> indexName.equals(i.getName()));
        } catch (Exception e) {
            log.warn("[mongo-health] failed to list indexes for {}: {}", collection, e.getMessage());
            return false;
        }
    }
}
