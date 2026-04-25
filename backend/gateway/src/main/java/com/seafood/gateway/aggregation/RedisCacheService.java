package com.seafood.gateway.aggregation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis-based caching service for aggregation layer.
 * Provides reactive caching with configurable TTL.
 */
@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final AggregationProperties aggregationProperties;
    private final ObjectMapper objectMapper;

    public RedisCacheService(ReactiveStringRedisTemplate redisTemplate, AggregationProperties aggregationProperties) {
        this.redisTemplate = redisTemplate;
        this.aggregationProperties = aggregationProperties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Get cached value by key.
     *
     * @param key cache key
     * @param <T> expected type
     * @return cached value or empty Mono if not found
     */
    public <T> Mono<T> get(String key, Class<T> type) {
        return redisTemplate.opsForValue().get(key)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, type);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to deserialize cached value for key: {}", key, e);
                        return null;
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Redis get failed for key: {}, error: {}", key, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Set value in cache with TTL from configuration.
     *
     * @param key cache key
     * @param value value to cache
     * @param <T> value type
     * @return Mono completes when value is cached
     */
    public <T> Mono<Void> set(String key, T value) {
        return set(key, value, Duration.ofMinutes(aggregationProperties.getCache().getTtlMinutes()));
    }

    /**
     * Set value in cache with custom TTL.
     *
     * @param key cache key
     * @param value value to cache
     * @param ttl time-to-live
     * @param <T> value type
     * @return Mono completes when value is cached
     */
    public <T> Mono<Void> set(String key, T value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            return redisTemplate.opsForValue().set(key, json, ttl)
                    .doOnSuccess(v -> log.debug("Cached value for key: {} with TTL: {}", key, ttl))
                    .onErrorResume(e -> {
                        log.warn("Redis set failed for key: {}, error: {}", key, e.getMessage());
                        return Mono.empty();
                    });
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize value for key: {}", key, e);
            return Mono.empty();
        }
    }

    /**
     * Delete cached value by key.
     *
     * @param key cache key
     * @return Mono completes when value is deleted
     */
    public Mono<Boolean> delete(String key) {
        return redisTemplate.delete(key)
                .map(count -> count > 0)
                .onErrorResume(e -> {
                    log.warn("Redis delete failed for key: {}, error: {}", key, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Check if key exists in cache.
     *
     * @param key cache key
     * @return true if key exists
     */
    public Mono<Boolean> exists(String key) {
        return redisTemplate.hasKey(key)
                .onErrorResume(e -> {
                    log.warn("Redis exists check failed for key: {}, error: {}", key, e.getMessage());
                    return Mono.just(false);
                });
    }
}
