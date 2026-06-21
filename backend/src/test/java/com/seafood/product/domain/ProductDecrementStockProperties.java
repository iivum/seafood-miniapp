package com.seafood.product.domain;

import com.seafood.shared.error.DomainException;
import com.seafood.testsupport.builders.ProductBuilder;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 5 C4 — {@code Product.decrementStock} 数值边界 property(tasks §2)。
 *
 * <p>用 jqwik 随机生成 stock / quantity,验证扣减的数值契约 —— example 测试只验有限点位,
 * property 用上千组样本逼反例,失败自动 shrink 到最小反例。
 */
class ProductDecrementStockProperties {

    /** ∀ 0 < quantity ≤ stock:结果 stock == 原 stock − quantity 且 ≥ 0(库存不变量)。 */
    @Property
    void decrement_withinStock_reducesExactlyAndStaysNonNegative(
            @ForAll @IntRange(min = 1, max = 1_000_000) int stock,
            @ForAll @IntRange(min = 1, max = 1_000_000) int quantity) {
        Assume.that(quantity <= stock);

        Product result = ProductBuilder.aProduct().withStock(stock).build()
                .decrementStock(quantity);

        assertThat(result.stock()).isEqualTo(stock - quantity);
        assertThat(result.stock()).isGreaterThanOrEqualTo(0);
    }

    /** ∀ quantity > stock:超量扣减恒抛 DomainException。 */
    @Property
    void decrement_exceedingStock_throws(
            @ForAll @IntRange(min = 0, max = 1_000_000) int stock,
            @ForAll @IntRange(min = 1, max = 2_000_000) int quantity) {
        Assume.that(quantity > stock);

        Product p = ProductBuilder.aProduct().withStock(stock).build();

        assertThatThrownBy(() -> p.decrementStock(quantity))
                .isInstanceOf(DomainException.class);
    }

    /** ∀ quantity ≤ 0:非正扣减恒抛 DomainException(防越权/无意义扣减)。 */
    @Property
    void decrement_nonPositiveQuantity_throws(
            @ForAll @IntRange(min = -1_000_000, max = 0) int quantity) {
        Product p = ProductBuilder.aProduct().withStock(100).build();

        assertThatThrownBy(() -> p.decrementStock(quantity))
                .isInstanceOf(DomainException.class);
    }
}
