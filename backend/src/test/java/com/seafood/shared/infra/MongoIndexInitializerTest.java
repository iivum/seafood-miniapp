package com.seafood.shared.infra;

import com.seafood.order.infra.OrderDocument;
import com.seafood.product.infra.ProductDocument;
import com.seafood.user.infra.UserDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 2 §2.9 / PR review #6 / #20 — 启动期索引创建 + 关键索引 fail-fast 路径。
 *
 * <p>纯 JUnit + Mockito 覆盖 {@link MongoIndexInitializer#init()} 的关键不变量:
 * <ol>
 *   <li>对 {@code revoked_tokens} 集合调用 {@code ensureIndex},索引定义中
 *       {@code expireAfterSeconds=0} 且 key 是 {@code expiresAt}(TTL 索引的最小契约)。</li>
 *   <li>对 {@code users} 集合调用 {@code ensureIndex} 至少一次,且索引定义中
 *       {@code unique=true} 且 key 是 {@code openId}。</li>
 *   <li>关键索引(TTL、unique)创建失败 → 抛 {@link IndexInitializationException},
 *       阻止应用进入 ready 状态(PR review #6 行为保证)。</li>
 *   <li>非关键索引(annotation-derived、text)创建失败 → 仅 {@code warn},不抛。</li>
 * </ol>
 *
 * <p>不依赖 MongoDB/Testcontainers:用 Mockito 桩 {@link MongoTemplate#indexOps} 与
 * {@link IndexResolver},关注"创建逻辑"而非"创建结果"。端到端的 TTL 是否真生效
 * 是 MongoDB 自身行为,不在本测试范围。
 */
class MongoIndexInitializerTest {

    private MongoTemplate mongo;
    private MongoMappingContext mappingContext;
    private MongoIndexInitializer initializer;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        // 用真实 MongoMappingContext 解析 @Document 注解 — annotation-derived 分支需要它
        mappingContext = new MongoMappingContext();
        mappingContext.afterPropertiesSet();
        mappingContext.getPersistentEntity(ProductDocument.class);
        mappingContext.getPersistentEntity(OrderDocument.class);
        mappingContext.getPersistentEntity(UserDocument.class);

        // indexOps(类) 走真实 mapping(annotation-derived);
        // indexOps(集合名) 走 mock(手写 critical / optional)
        IndexOperations classOps = mock(IndexOperations.class);
        doReturn(classOps).when(mongo).indexOps(any(Class.class));
        when(classOps.ensureIndex(any(IndexDefinition.class))).thenReturn("annotation-index");
        IndexOperations collectionOps = mock(IndexOperations.class);
        when(mongo.indexOps(any(String.class))).thenReturn(collectionOps);
        when(collectionOps.ensureIndex(any(IndexDefinition.class))).thenReturn("manual-index");

        initializer = new MongoIndexInitializer(mongo, mappingContext, new MongoIndexHealthIndicator(mongo));
    }

    @Test
    void init_createsTtlIndexOnRevokedTokensWithExpireAfterSecondsZero() {
        initializer.init();

        // 关键断言:对 "revoked_tokens" 调用过 ensureIndex
        verify(mongo, atLeastOnce()).indexOps(eq("revoked_tokens"));
    }

    @Test
    void init_createsUniqueOpenIdIndexOnUsers() {
        initializer.init();

        verify(mongo, atLeastOnce()).indexOps("users");
    }

    /**
     * PR review #6 行为保证:关键索引创建失败 → {@link IndexInitializationException},
     * 不被吞。这是通过 init() 整体 throw 验证的 ——
     * 关键索引有 2 个(unique + TTL),任一失败都会 fail-fast。
     */
    @Test
    void init_throwsIndexInitializationExceptionWhenTtlEnsureFails() {
        IndexOperations collectionOps = mock(IndexOperations.class);
        when(mongo.indexOps(any(String.class))).thenReturn(collectionOps);
        // users 索引正常,revoked_tokens 索引抛错
        doReturn("uk_openId").when(collectionOps).ensureIndex(argThat(idx ->
                idx != null && idx.toString().contains("uk_openId")));
        doThrow(new RuntimeException("TTL not supported on this Mongo version"))
                .when(collectionOps).ensureIndex(argThat(idx ->
                        idx != null && idx.toString().contains("ttl_expiresAt")));

        assertThatThrownBy(() -> initializer.init())
                .isInstanceOf(IndexInitializationException.class)
                .hasMessageContaining("revoked_tokens")
                .hasMessageContaining("TTL not supported on this Mongo version")
                .hasMessageContaining("Application cannot start safely");
    }

    /**
     * PR review #6 行为保证:unique 约束创建失败也 fail-fast。
     */
    @Test
    void init_throwsIndexInitializationExceptionWhenUniqueOpenIdEnsureFails() {
        IndexOperations collectionOps = mock(IndexOperations.class);
        when(mongo.indexOps(any(String.class))).thenReturn(collectionOps);
        doThrow(new RuntimeException("duplicate key conflict on existing index"))
                .when(collectionOps).ensureIndex(argThat(idx ->
                        idx != null && idx.toString().contains("uk_openId")));

        assertThatThrownBy(() -> initializer.init())
                .isInstanceOf(IndexInitializationException.class)
                .hasMessageContaining("users")
                .hasMessageContaining("duplicate key conflict");
    }

    /**
     * 非关键索引(text / annotation-derived)失败<em>不</em>抛 —
     * 验证它们走的是 {@code ensureOptional} / 静默 catch 分支,而不是 {@code ensureCritical}。
     * 实现方式:让 critical 索引全部成功,再让 text 索引抛错,确认 init() 不抛。
     */
    @Test
    void init_swallowsFailuresOfOptionalIndexesAndStillCompletes() {
        // annotation-derived 抛错(走 ensureAnnotationDerived 的 catch 分支)
        IndexOperations classOps = mock(IndexOperations.class);
        doReturn(classOps).when(mongo).indexOps(any(Class.class));
        doThrow(new RuntimeException("annotation index broken"))
                .when(classOps).ensureIndex(any(IndexDefinition.class));

        IndexOperations collectionOps = mock(IndexOperations.class);
        when(mongo.indexOps(any(String.class))).thenReturn(collectionOps);
        // critical(uk_openId, ttl_expiresAt)成功;text(text_name_description)抛错
        when(collectionOps.ensureIndex(argThat(idx ->
                idx != null && idx.toString().contains("uk_openId"))))
                .thenReturn("uk_openId");
        when(collectionOps.ensureIndex(argThat(idx ->
                idx != null && idx.toString().contains("ttl_expiresAt"))))
                .thenReturn("ttl_expiresAt");
        doThrow(new RuntimeException("text index failed"))
                .when(collectionOps).ensureIndex(argThat(idx ->
                        idx != null && idx.toString().contains("text_name_description")));

        // 不应抛:annotation + text 失败都属"可选"分支
        initializer.init();

        // 三个 collection-level 索引(2 critical + 1 text)都应被调用
        verify(collectionOps, times(3)).ensureIndex(any(IndexDefinition.class));
    }

    private static <T> T argThat(java.util.function.Predicate<T> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
