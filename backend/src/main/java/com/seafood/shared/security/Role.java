package com.seafood.shared.security;

/**
 * 业务角色(对齐 design.md §4.3 端点访问矩阵)。
 * Spring Security 中以 {@code ROLE_} 前缀参与 {@code hasRole(...)} 判定。
 */
public enum Role {
    CUSTOMER,
    ADMIN
}
