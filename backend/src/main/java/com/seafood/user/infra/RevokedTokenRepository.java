package com.seafood.user.infra;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 撤销 token 的 MongoDB 仓储(参见 design.md §3,specs/auth §Token revocation)。
 *
 * <p>主访问模式:
 * <ul>
 *   <li>{@link #existsById(Object)} — 由 {@code TokenRevocationService.isRevoked(jti)} 调用,
 *       走 {@code _id} 索引 O(1)</li>
 *   <li>{@link #findByUserId(String)} — 由 admin force-logout 端点审计使用</li>
 *   <li>{@link #deleteByUserId(String)} — 当前未用,保留供运维清理脚本</li>
 * </ul>
 */
public interface RevokedTokenRepository extends MongoRepository<RevokedToken, String> {

    List<RevokedToken> findByUserId(String userId);

    long deleteByUserId(String userId);
}
