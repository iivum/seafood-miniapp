package com.seafood.shared.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PR review I9 — {@link MongoIndexHealthIndicator} 单测。
 *
 * <p>该 indicator 是 push-sweep #2 的核心修复 —— critical 索引丢失必须
 * 触发 readinessProbe 失败。直接覆盖三态(UNKNOWN / UP / DOWN)+ listIndex
 * 异常分支。
 */
class MongoIndexHealthIndicatorTest {

    private MongoTemplate mongo;
    private MongoIndexHealthIndicator indicator;
    private IndexOperations usersOps;
    private IndexOperations revokedOps;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        usersOps = mock(IndexOperations.class);
        revokedOps = mock(IndexOperations.class);
        // PR review:不要在 setUp 里用 eq()/any() 配合 when().thenReturn() ——
        // Mockito 的 matcher 在 setUp 时 unresolved,会被记为 unfinished stubbing
        // 并污染后面所有测试。用 doReturn().when() 模式即可。
        doReturn(usersOps).when(mongo).indexOps("users");
        doReturn(revokedOps).when(mongo).indexOps("revoked_tokens");
        indicator = new MongoIndexHealthIndicator(mongo);
    }

    @Test
    void health_isUnknownBeforeCriticalIndexesEnsured() {
        Health h = indicator.health();
        assertThat(h.getStatus())
                .as("init 未完成前必须返 UNKNOWN,readiness 不能误标 UP")
                .isEqualTo(Status.UNKNOWN);
    }

    @Test
    void health_isUpAfterCriticalIndexesEnsured_whenAllPresent() {
        when(usersOps.getIndexInfo()).thenReturn(List.of(
                indexInfo("uk_openId")));
        when(revokedOps.getIndexInfo()).thenReturn(List.of(
                indexInfo("ttl_expiresAt")));

        indicator.onCriticalIndexesEnsured();
        Health h = indicator.health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails())
                .containsEntry("users.uk_openId", "present")
                .containsEntry("revoked_tokens.ttl_expiresAt", "present");
    }

    @Test
    void health_isDown_whenUsersUniqueIndexMissing() {
        // 关键:运维误删 uk_openId → 必须 DOWN。攻击者用同 openId 重复建账号
        // 的风险(PR review #2/#6 关心的场景)。
        when(usersOps.getIndexInfo()).thenReturn(List.of());   // 空
        when(revokedOps.getIndexInfo()).thenReturn(List.of(
                indexInfo("ttl_expiresAt")));

        indicator.onCriticalIndexesEnsured();
        Health h = indicator.health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails()).containsEntry("users.uk_openId", "MISSING");
    }

    @Test
    void health_isDown_whenTtlIndexMissing() {
        // revoked_tokens 的 TTL 索引被运维误删 → revoked tokens 永远留在 DB
        // (PR review #6 担心的 O(n) 退化为 auth check 瓶颈)。
        when(usersOps.getIndexInfo()).thenReturn(List.of(
                indexInfo("uk_openId")));
        when(revokedOps.getIndexInfo()).thenReturn(List.of());

        indicator.onCriticalIndexesEnsured();
        Health h = indicator.health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails()).containsEntry("revoked_tokens.ttl_expiresAt", "MISSING");
    }

    @Test
    void health_listIndexExceptionIsTreatedAsMissing() {
        // Mongo 暂时不可达 / 权限问题 → listIndex 抛异常
        // 保守策略:按 "MISSING" 处理,触发 readiness 失败,避免在 Mongo 异常时
        // 误标 UP 让流量进错环境。
        when(usersOps.getIndexInfo()).thenThrow(new RuntimeException("mongo down"));
        when(revokedOps.getIndexInfo()).thenReturn(List.of(
                indexInfo("ttl_expiresAt")));

        indicator.onCriticalIndexesEnsured();
        Health h = indicator.health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails()).containsEntry("users.uk_openId", "MISSING");
    }

    /**
     * Spring Data 的 IndexInfo 公共构造器:IndexInfo(List, name, unique, sparse, language)。
     * Mockito 默认 mock-maker 不支持 final 方法,而 IndexInfo 的几个 getter 是 final,
     * 用 mock(IndexInfo.class) 会 UnfinishedStubbingException。直接用真实实例更稳。
     */
    private static IndexInfo indexInfo(String name) {
        return new IndexInfo(java.util.List.of(), name, false, false, "");
    }
}
