package com.seafood.order.application;

import java.math.BigDecimal;

/**
 * fix-order-amount-contract:运费 + 优惠计算的唯一权威(design.md 决策 1)。
 *
 * <p>与 mp {@code order-confirm.js} 的 {@code SHIPPING_FEE_MAP}/{@code calcDiscount}
 * 数值锁死一致(两侧各自维护一份,靠双方测试守不漂移,见 design.md 决策 2 的取舍)——
 * mp 端只做"本地预估显示",真正落库的 {@code totalAmount} 权威计算在这里。
 */
public final class OrderPricing {

    /** 免运费(默认档,mp 默认选中项)。 */
    public static final String SHIPPING_FREE = "FREE";
    /** 顺丰速运。 */
    public static final String SHIPPING_SF = "SF";
    /** 中通快递。 */
    public static final String SHIPPING_ZTO = "ZTO";

    private static final BigDecimal FEE_FREE = BigDecimal.ZERO;
    private static final BigDecimal FEE_SF = new BigDecimal("12");
    private static final BigDecimal FEE_ZTO = new BigDecimal("8");

    private static final BigDecimal DISCOUNT_THRESHOLD = new BigDecimal("100");
    private static final BigDecimal DISCOUNT_AMOUNT = new BigDecimal("10");

    private OrderPricing() {
        // 工具类禁止实例化
    }

    /**
     * 按配送方式查运费表。{@code null}/未识别值一律按 {@link #SHIPPING_FREE} 兜底
     * (design.md 决策 1 风险清单:直接购买路径此前不带 shippingMethod,缺省按
     * mp 默认选中项对齐,而不是拒绝建单)。
     */
    public static BigDecimal shippingFeeFor(String shippingMethod) {
        if (SHIPPING_SF.equals(shippingMethod)) {
            return FEE_SF;
        }
        if (SHIPPING_ZTO.equals(shippingMethod)) {
            return FEE_ZTO;
        }
        return FEE_FREE;
    }

    /** 满 100 减 10(与 mp {@code calcDiscount} 同规则,{@code subtotal >= 100} 触发)。 */
    public static BigDecimal discountFor(BigDecimal subtotal) {
        if (subtotal != null && subtotal.compareTo(DISCOUNT_THRESHOLD) >= 0) {
            return DISCOUNT_AMOUNT;
        }
        return BigDecimal.ZERO;
    }
}
