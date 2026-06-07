package com.seafood.bff.admin;

import com.seafood.user.application.TokenRevocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin 用户管理端点(Sprint 2 §3.7,specs/auth §Admin force-logout user)。
 *
 * <p>{@code POST /api/admin/users/{id}/revoke-tokens} 让 admin 把某 user 的
 * 全部已签发 access token 立即作废 —— 走 {@link TokenRevocationService#revokeAllForUser}
 * 写一个 sentinel 文档;{@code JwtAuthenticationFilter} 见到该 userId 后续请求
 * 即返 401 TOKEN_REVOKED。
 *
 * <p>约束(对齐 design §3 decision 3 + 跨模块约束):
 * <ul>
 *   <li>只通过 ApplicationService 跨模块,绝不直接碰 Repository</li>
 *   <li>ADMIN-only(类级 {@code @PreAuthorize} + SecurityConfig URL 规则双重防护)</li>
 * </ul>
 *
 * <p>注意:本类不重复实现 {@code AdminBffController} 的"只读 BFF 聚合"职责,
 * 只放"管理动作"端点(revoke-tokens 是写操作,放这里语义更准)。
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final TokenRevocationService revocations;

    public AdminUserController(TokenRevocationService revocations) {
        this.revocations = revocations;
    }

    /**
     * 强制登出某 user:把他所有尚未过期的 access token 全部作废。
     *
     * <p>实现细节:见 {@link TokenRevocationService#revokeAllForUser(String)}
     * —— 写一个 {@code _id = "u:USER_ID"} 的 sentinel 文档;{@code isRevoked}
     * 检测到该前缀即返回 true。该文档 {@code expiresAt} 设为 100 年后,
     * 实际清理交由 ops job(本次未实现)。
     */
    @PostMapping("/{id}/revoke-tokens")
    public ResponseEntity<Void> revokeTokens(@PathVariable("id") String userId) {
        revocations.revokeAllForUser(userId);
        return ResponseEntity.noContent().build();
    }
}
