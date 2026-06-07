package com.seafood.shared.infra;

/**
 * 启动期<em>关键</em>索引创建失败 — 阻止应用进入 ready 状态(Sprint 2 PR review #6)。
 *
 * <p>与 {@code @ConfigurationProperties} 校验异常一样,这是 fail-fast 异常:
 * 抛在 {@code ApplicationReadyEvent} 监听器里不会回滚已发出的 ready 事件,但会让进程
 * 继续带着该异常跑到主线程,触发 {@code @EventListener} 默认的 {@code ERROR} 日志;
 * 实际生产部署应配合 {@code /actuator/health/readiness} 的额外保护(参见 design §5.3)。
 */
public class IndexInitializationException extends RuntimeException {

    public IndexInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
