package com.seafood.user.domain.model;

/**
 * 令牌仓库接口
 */
public interface TokenRepository {
    /**
     * 保存访问令牌
     * @param token JWT令牌
     * @param userId 用户ID
     */
    void saveAccessToken(String token, String userId);

    /**
     * 保存刷新令牌
     * @param refreshToken 刷新令牌
     * @param userId 用户ID
     */
    void saveRefreshToken(String refreshToken, String userId);

    /**
     * 根据令牌获取用户ID
     * @param token JWT令牌
     * @return 用户ID，如果令牌无效或过期则返回null
     */
    String getUserIdByToken(String token);

    /**
     * 验证令牌是否有效
     * @param token JWT令牌
     * @return 是否有效
     */
    boolean validateToken(String token);

    /**
     * 删除用户的访问令牌
     * @param token JWT令牌
     * @param userId 用户ID
     */
    void deleteAccessToken(String token, String userId);

    /**
     * 删除用户的刷新令牌
     * @param refreshToken 刷新令牌
     * @param userId 用户ID
     */
    void deleteRefreshToken(String refreshToken, String userId);

    /**
     * 将令牌加入黑名单（用于登出）
     * @param token JWT令牌
     */
    void blacklistToken(String token);

    /**
     * 检查令牌是否在黑名单中
     * @param token JWT令牌
     * @return 是否在黑名单中
     */
    boolean isTokenBlacklisted(String token);

    /**
     * 删除用户的所有令牌（用于强制登出）
     * @param userId 用户ID
     */
    void deleteAllUserTokens(String userId);

    /**
     * 获取用户的活跃令牌数量
     * @param userId 用户ID
     * @return 活跃令牌数量
     */
    int getUserActiveTokenCount(String userId);

    /**
     * 清理过期的令牌
     * 该方法应该定期执行（例如每天一次）
     */
    void cleanupExpiredTokens();
}