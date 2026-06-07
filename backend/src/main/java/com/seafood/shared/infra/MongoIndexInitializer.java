package com.seafood.shared.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.stereotype.Component;

import com.seafood.product.infra.ProductDocument;
import com.seafood.order.infra.OrderDocument;
import com.seafood.user.infra.UserDocument;

/**
 * 启动时建索引(design §6.2,specs/backend-api §Native Image safety)。
 *
 * <p>两个来源:
 * <ol>
 *   <li>由 {@link MongoPersistentEntityIndexResolver} 从 {@code @Document} / {@code @Indexed}
 *       注解解析出来的索引(Product/Order/User)</li>
 *   <li>手写的额外索引 — 例如 users.openId unique、products 文本索引、orders.createdAt 倒序</li>
 * </ol>
 *
 * <p>{@code @EventListener(ApplicationReadyEvent.class)}:确保只在容器启动成功后建;
 * 用 {@code createIndex} 幂等(同名同 keySpecs 重复调用 OK)。
 *
 * <p><b>关键 vs 非关键索引(PR review #6):</b>
 * <ul>
 *   <li>关键(unique 约束、TTL 索引)— 创建失败必须 fail-fast:unique 不存在会让
 *       {@code users.openId} 重复入库(同 openId 多次创建账号);TTL 缺失会让 revoked
 *       tokens 永远留在 DB(每条都进 auth check,O(n) 退化为 DoS 入口)。两者都是
 *       安全/合规红线,失败时抛 {@link IndexInitializationException} 阻断应用进入 ready 状态。</li>
 *   <li>非关键(annotation-derived、text 索引)— 失败时仅 {@code warn}:query 性能降级
 *       而非安全/正确性问题,不应让应用起不来。</li>
 * </ul>
 */
@Component
public class MongoIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongo;
    private final MongoMappingContext mappingContext;
    /**
     * PR review push-sweep #2:critical 索引完成时通知 health indicator,
     * 把 criticalIndexesEnsured 翻为 true。这样 {@code /actuator/health/mongoIndexes}
     * 从 UNKNOWN(启动中)切到 UP,或 critical 失败时停在 UNKNOWN/触发后续 ready 探针失败。
     */
    private final MongoIndexHealthIndicator healthIndicator;

    public MongoIndexInitializer(MongoTemplate mongo,
                                 MongoMappingContext mappingContext,
                                 MongoIndexHealthIndicator healthIndicator) {
        this.mongo = mongo;
        this.mappingContext = mappingContext;
        this.healthIndicator = healthIndicator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // annotation-derived:performance-only,失败仅 warn
        ensureAnnotationDerived(ProductDocument.class);
        ensureAnnotationDerived(OrderDocument.class);
        ensureAnnotationDerived(UserDocument.class);

        // text index:performance-only,失败仅 warn
        ensureOptional("products",
                new Index().on("name", org.springframework.data.domain.Sort.Direction.ASC)
                        .on("description", org.springframework.data.domain.Sort.Direction.ASC)
                        .named("text_name_description"));

        // unique constraint on openId:security-critical — 缺失会让同一 openId 建多个账号
        // PR review I7:两个 critical 索引<em>都</em>跑,失败聚合,而不是第一个失败就 halt
        // 后续。这样部署期能一次性看到所有坏掉的索引,不用重启 pod 才能发现下一个。
        java.util.List<IndexInitializationException> criticalFailures = new java.util.ArrayList<>();
        try {
            ensureCritical("users",
                    new Index().on("openId", org.springframework.data.domain.Sort.Direction.ASC)
                            .unique()
                            .named("uk_openId"),
                    "unique openId — prevents duplicate accounts for the same WeChat user");
        } catch (IndexInitializationException e) {
            criticalFailures.add(e);
        }

        // Sprint 2 §3.3 — revoked_tokens TTL index:文档到期后 MongoDB 后台线程
        // 自动删除(每 60s 扫一次),无需应用层清理。expireAfterSeconds=0 表示
        // "expiresAt 字段本身的取值即为到期时间"。design.md §3 decision 3。
        // 关键:缺失会让 revoked token 永远留在 DB,O(n) 退化为 auth check 瓶颈。
        try {
            ensureCritical("revoked_tokens",
                    new Index().on("expiresAt", org.springframework.data.domain.Sort.Direction.ASC)
                            .expire(0L)
                            .named("ttl_expiresAt"),
                    "TTL on revoked_tokens.expiresAt — bounds revoked-token collection size");
        } catch (IndexInitializationException e) {
            criticalFailures.add(e);
        }

        if (!criticalFailures.isEmpty()) {
            // 聚合所有 critical 失败 — 运维一次看到全貌,而不是重启 pod 才发现下一个
            StringBuilder msg = new StringBuilder("Critical index creation failed: ");
            for (int i = 0; i < criticalFailures.size(); i++) {
                if (i > 0) msg.append("; ");
                msg.append('[').append(i + 1).append("] ").append(criticalFailures.get(i).getMessage());
            }
            throw new IndexInitializationException(msg.toString(),
                    criticalFailures.get(0));  // cause 取首个;full detail 在 message
        }

        log.info("[mongo] all indexes ensured");
        // PR review #2:通知 health indicator,让 /actuator/health/mongoIndexes 切到 UP。
        // 若 ensureCritical 抛 IndexInitializationException,本行不会执行,
        // health probe 继续返 UNKNOWN → readiness 探针失败,k8s 不会路由流量。
        healthIndicator.onCriticalIndexesEnsured();
    }

    private void ensureAnnotationDerived(Class<?> docClass) {
        IndexOperations ops = mongo.indexOps(docClass);
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);
        resolver.resolveIndexFor(docClass).forEach(def -> {
            try {
                ops.ensureIndex(def);
            } catch (Exception e) {
                log.warn("[mongo] ensureIndex {} failed: {}", def, e.getMessage());
            }
        });
    }

    /** Performance-only extra index — 失败仅 warn。 */
    private void ensureOptional(String collection, Index index) {
        try {
            mongo.indexOps(collection).ensureIndex(index);
        } catch (Exception e) {
            log.warn("[mongo] ensureIndex {} on {} failed: {}", index, collection, e.getMessage());
        }
    }

    /**
     * Security/compliance-critical index — 失败必须 fail-fast,否则应用带着缺失索引进入
     * {@code /actuator/health = UP} 状态,把安全/正确性风险埋进生产。
     *
     * <p>注意:抛在 {@code ApplicationReadyEvent} 监听器里 <em>不会</em>中断已发出的 ready
     * 事件;但会进 ERROR 日志并阻止后续索引创建。运维在启动期会看到,等价的"阻止 ready" 效果
     * 应当配合 readinessProbe / 健康检查单独实现(参见 design §5.3)。
     */
    private void ensureCritical(String collection, Index index, String why) {
        try {
            mongo.indexOps(collection).ensureIndex(index);
        } catch (Exception e) {
            log.error("[mongo] CRITICAL ensureIndex {} on {} FAILED: {} — {}",
                    index, collection, e.getMessage(), why);
            throw new IndexInitializationException(
                    "Failed to ensure critical index on '" + collection
                            + "': " + e.getMessage() + ". Application cannot start safely. Cause: " + why,
                    e);
        }
    }
}
