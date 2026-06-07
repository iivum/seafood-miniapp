package com.seafood.shared.security;

/**
 * PR review I6:admin 路径判定共享工具。
 *
 * <p>原 {@link JwtAuthenticationFilter#isAdminPath(String)} 与
 * {@code AdminRateLimitFilter.shouldNotFilter} 各有一份相同实现 ——
 * 任何一边改了判定,另一边若不同步会引入安全/性能 bug。本类提供
 * 唯一权威定义,两边 filter 都调它。
 *
 * <p>判定规则:
 * <ul>
 *   <li>{@code /api/admin} — 精确匹配(无尾斜杠)</li>
 *   <li>{@code /api/admin/**} — 前缀匹配(必须带尾斜杠)</li>
 *   <li>近似前缀(例 {@code /api/adminalice})<em>不</em>算 admin</li>
 * </ul>
 */
public final class AdminPathMatcher {

    private static final String ADMIN_PATH_EXACT = "/api/admin";
    private static final String ADMIN_PATH_PREFIX = "/api/admin/";

    private AdminPathMatcher() {
    }

    public static boolean isAdminPath(String uri) {
        if (uri == null) {
            return false;
        }
        return ADMIN_PATH_EXACT.equals(uri) || uri.startsWith(ADMIN_PATH_PREFIX);
    }
}
