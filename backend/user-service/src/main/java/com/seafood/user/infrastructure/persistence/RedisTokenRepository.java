package com.seafood.user.infrastructure.persistence;

import com.seafood.common.security.JwtProperties;
import com.seafood.user.domain.model.TokenRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

/**
 * Redis实现的令牌存储库
 * 替代内存HashMap，提供持久化和可扩展的令牌存储
 */
@Repository
public class RedisTokenRepository implements TokenRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final JwtProperties jwtProperties;

    // Redis键前缀
    private static final String ACCESS_TOKEN_KEY_PREFIX = "token:access:";
    private static final String REFRESH_TOKEN_KEY_PREFIX = "token:refresh:";
    private static final String TOKEN_BLACKLIST_KEY_PREFIX = "token:blacklist:";
    private static final String USER_TOKENS_KEY_PREFIX = "user:tokens:";

    public RedisTokenRepository(RedisTemplate<String, String> redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    /**
     * 保存访问令牌
     * @param token JWT令牌
     * @param userId 用户ID
     */
    @Override
    public void saveAccessToken(String token, String userId) {
        String key = ACCESS_TOKEN_KEY_PREFIX + token;
        String userTokensKey = USER_TOKENS_KEY_PREFIX + userId;

        // 保存令牌到用户ID的映射
        redisTemplate.opsForValue().set(
            key,
            userId,
            jwtProperties.getExpiration(),
            TimeUnit.MILLISECONDS
        );

        // 将令牌添加到用户的令牌集合
        redisTemplate.opsForSet().add(userTokensKey, token);
        // 设置用户令牌集合的过期时间（比单个令牌长一些）
        redisTemplate.expire(userTokensKey, jwtProperties.getExpiration() + 3600000, TimeUnit.MILLISECONDS);
    }

    /**
     * 保存刷新令牌
     * @param refreshToken 刷新令牌
     * @param userId 用户ID
     */
    @Override
    public void saveRefreshToken(String refreshToken, String userId) {
        String key = REFRESH_TOKEN_KEY_PREFIX + refreshToken;
        String userTokensKey = USER_TOKENS_KEY_PREFIX + userId;

        // 保存刷新令牌
        redisTemplate.opsForValue().set(
            key,
            userId,
            jwtProperties.getRefreshExpiration(),
            TimeUnit.MILLISECONDS
        );

        // 将刷新令牌添加到用户的令牌集合
        redisTemplate.opsForSet().add(userTokensKey, refreshToken);
        redisTemplate.expire(userTokensKey, jwtProperties.getRefreshExpiration() + 3600000, TimeUnit.MILLISECONDS);
    }

    /**
     * 根据令牌获取用户ID
     * @param token JWT令牌
     * @return 用户ID，如果令牌无效或过期则返回null
     */
    @Override
    public String getUserIdByToken(String token) {
        // 检查是否在黑名单中
        if (isTokenBlacklisted(token)) {
            return null;
        }

        // 先尝试从访问令牌获取
        String accessTokenKey = ACCESS_TOKEN_KEY_PREFIX + token;
        String userId = redisTemplate.opsForValue().get(accessTokenKey);

        if (userId == null) {
            // 尝试从刷新令牌获取
            String refreshTokenKey = REFRESH_TOKEN_KEY_PREFIX + token;
            userId = redisTemplate.opsForValue().get(refreshTokenKey);
        }

        return userId;
    }

    /**
     * 验证令牌是否有效
     * @param token JWT令牌
     * @return 是否有效
     */
    @Override
    public boolean validateToken(String token) {
        return getUserIdByToken(token) != null;
    }

    /**
     * 删除用户的访问令牌
     * @param token JWT令牌
     * @param userId 用户ID
     */
    @Override
    public void deleteAccessToken(String token, String userId) {
        String key = ACCESS_TOKEN_KEY_PREFIX + token;
        String userTokensKey = USER_TOKENS_KEY_PREFIX + userId;

        // 从Redis删除
        redisTemplate.delete(key);
        // 从用户令牌集合中移除
        redisTemplate.opsForSet().remove(userTokensKey, token);
    }

    /**
     * 删除用户的刷新令牌
     * @param refreshToken 刷新令牌
     * @param userId 用户ID
     */
    @Override
    public void deleteRefreshToken(String refreshToken, String userId) {
        String key = REFRESH_TOKEN_KEY_PREFIX + refreshToken;
        String userTokensKey = USER_TOKENS_KEY_PREFIX + userId;

        redisTemplate.delete(key);
        redisTemplate.opsForSet().remove(userTokensKey, refreshToken);
    }

    /**
     * 将令牌加入黑名单（用于登出）
     * @param token JWT令牌
     */
    @Override
    public void blacklistToken(String token) {
        String key = TOKEN_BLACKLIST_KEY_PREFIX + token;

        // 将令牌加入黑名单，过期时间与访问令牌相同
        redisTemplate.opsForValue().set(
            key,
            "1",
            jwtProperties.getExpiration(),
            TimeUnit.MILLISECONDS
        );

        // 从活跃令牌列表中移除
        removeFromActiveTokens(token);
    }

    /**
     * 检查令牌是否在黑名单中
     * @param token JWT令牌
     * @return 是否在黑名单中
     */
    @Override
    public boolean isTokenBlacklisted(String token) {
        String key = TOKEN_BLACKLIST_KEY_PREFIX + token;
        return redisTemplate.hasKey(key);
    }

    /**
     * 删除用户的所有令牌（用于强制登出）
     * @param userId 用户ID
     */
    @Override
    public void deleteAllUserTokens(String userId) {
        String userTokensKey = USER_TOKENS_KEY_PREFIX + userId;

        // 获取用户的所有令牌
        var tokens = redisTemplate.opsForSet().members(userTokensKey);
        if (tokens != null) {
            for (String token : tokens) {
                // 将令牌加入黑名单
                blacklistToken(token);
            }
        }

        // 删除用户的令牌集合
        redisTemplate.delete(userTokensKey);
    }

    /**
     * 获取用户的活跃令牌数量
     * @param userId 用户ID
     * @return 活跃令牌数量
     */
    @Override
    public int getUserActiveTokenCount(String userId) {
        String userTokensKey = USER_TOKENS_KEY_PREFIX + userId;
        Long count = redisTemplate.opsForSet().size(userTokensKey);
        return count != null ? count.intValue() : 0;
    }

    /**
     * 从活跃令牌列表中移除
     * @param token JWT令牌
     */
    private void removeFromActiveTokens(String token) {
        // 尝试从访问令牌中查找用户
        String accessTokenKey = ACCESS_TOKEN_KEY_PREFIX + token;
        String userId = redisTemplate.opsForValue().get(accessTokenKey);

        if (userId != null) {
            deleteAccessToken(token, userId);
            return;
        }

        // 尝试从刷新令牌中查找用户
        String refreshTokenKey = REFRESH_TOKEN_KEY_PREFIX + token;
        userId = redisTemplate.opsForValue().get(refreshTokenKey);

        if (userId != null) {
            deleteRefreshToken(token, userId);
        }
    }

    /**
     * 清理过期的令牌
     * 该方法应该定期执行（例如每天一次）
     */
    @Override
    public void cleanupExpiredTokens() {
        // 注意：这个方法在Redis集群环境下需要小心处理
        // 实际生产环境中应该使用Redis的过期机制，这个方法仅作为补充

        // 可以扫描过期的键，但通常Redis会自动处理过期键
        // 这里主要是为了清理可能遗漏的键
    }
}