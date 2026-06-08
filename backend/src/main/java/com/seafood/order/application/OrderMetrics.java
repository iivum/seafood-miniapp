package com.seafood.order.application;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 订单域指标工具(OpenSpec setup-observability-stack PR #3 / design §ADR-OQ3)。
 *
 * <p>唯一职责:把 {@code BigDecimal} 订单金额映射到 4 档几何分桶字符串,供
 * {@link OrderService#markPaid} 拼成 Prometheus tag(避免 userId/orderId/productId/email
 * 这类高基数字段被当成 tag 写入 — ArchUnit 规则见
 * {@code com.seafood.architecture.MetricsCardinalityTest},design §D5)。
 *
 * <p>分桶边界:
 * <ul>
 *   <li>{@code amount <  100}        → {@code lt100}</li>
 *   <li>{@code 100 <= amount <  500} → {@code 100to500}</li>
 *   <li>{@code 500 <= amount < 2000} → {@code 500to2000}</li>
 *   <li>{@code amount >= 2000}       → {@code gte2000}</li>
 * </ul>
 *
 * <p>所有 bucket 字符串是 {@code final}、白名单有限集 — Prometheus tag value
 * 不可枚举会导致时间序列爆炸。
 */
public final class OrderMetrics {

    /** Prometheus tag value:订单金额 < 100 元。 */
    public static final String BUCKET_LT_100 = "lt100";
    /** Prometheus tag value:100 ≤ 金额 < 500 元。 */
    public static final String BUCKET_100_TO_500 = "100to500";
    /** Prometheus tag value:500 ≤ 金额 < 2000 元。 */
    public static final String BUCKET_500_TO_2000 = "500to2000";
    /** Prometheus tag value:金额 ≥ 2000 元。 */
    public static final String BUCKET_GTE_2000 = "gte2000";

    /** 所有合法 bucket 值的白名单(ArchUnit 反射扫描时使用)。 */
    public static final Set<String> ALL_BUCKETS = Set.of(
            BUCKET_LT_100, BUCKET_100_TO_500, BUCKET_500_TO_2000, BUCKET_GTE_2000);

    private static final BigDecimal BOUND_100 = new BigDecimal("100");
    private static final BigDecimal BOUND_500 = new BigDecimal("500");
    private static final BigDecimal BOUND_2000 = new BigDecimal("2000");

    private OrderMetrics() {
        // 工具类禁止实例化
    }

    /**
     * 把订单金额映射到 4 档分桶标签值。
     *
     * @param amount 订单金额,不能为 {@code null}
     * @return 4 档之一:{@code lt100} / {@code 100to500} / {@code 500to2000} / {@code gte2000}
     * @throws NullPointerException {@code amount == null}
     * @throws IllegalArgumentException {@code amount < 0}
     */
    public static String bucketize(BigDecimal amount) {
        if (amount == null) {
            throw new NullPointerException("amount must not be null");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "amount must be non-negative, got: " + amount.toPlainString());
        }
        if (amount.compareTo(BOUND_100) < 0) {
            return BUCKET_LT_100;
        }
        if (amount.compareTo(BOUND_500) < 0) {
            return BUCKET_100_TO_500;
        }
        if (amount.compareTo(BOUND_2000) < 0) {
            return BUCKET_500_TO_2000;
        }
        return BUCKET_GTE_2000;
    }
}
