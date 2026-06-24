package com.seafood.featureflag.application;

import com.seafood.featureflag.domain.FeatureFlag;
import com.seafood.featureflag.infra.FeatureFlagMapper;
import com.seafood.featureflag.infra.FeatureFlagRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Feature flag 内存缓存，启动预加载 + 定时刷新（每 60 秒），避免每次请求查 DB。
 *
 * <p>缓存<strong>所有</strong> flag（含 disabled），否则 Service 无法区分
 * "flag 不存在"（默认 false）与"flag 存在但被禁用"（同样 false 但语义不同）。
 *
 * <p>不引入动态刷新 Bean（GraalVM Native 不兼容，参见 CLAUDE.md 硬规则）。
 */
@Component
public class FeatureFlagCache {

    private final FeatureFlagRepository repository;
    private volatile Map<String, FeatureFlag> cache = Map.of();

    public FeatureFlagCache(FeatureFlagRepository repository) {
        this.repository = repository;
    }

    /**
     * 启动时预加载缓存（fail-fast：MongoDB 不可用时直接抛出，阻断应用 ready）。
     */
    @PostConstruct
    void load() {
        refresh();
    }

    /**
     * 每 60 秒定时刷新缓存，确保管理员操作后的变更最终可见。
     * Service 层在写操作后会立即调用此方法以实现近实时更新。
     */
    @Scheduled(fixedDelay = 60_000)
    public void refresh() {
        cache = repository.findAll().stream()
                .collect(toUnmodifiableMap(
                        d -> FeatureFlagMapper.toDomain(d).flagKey(),
                        FeatureFlagMapper::toDomain));
    }

    /**
     * 从内存缓存中查询指定 flagKey 对应的 FeatureFlag。
     *
     * @param flagKey flag 唯一标识
     * @return 缓存中的 flag，不存在时返回 {@code Optional.empty()}
     */
    public Optional<FeatureFlag> get(String flagKey) {
        return Optional.ofNullable(cache.get(flagKey));
    }
}
