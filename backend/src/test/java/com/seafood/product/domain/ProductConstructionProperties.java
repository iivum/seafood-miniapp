package com.seafood.product.domain;

import com.seafood.shared.error.DomainException;
import com.seafood.testsupport.builders.ProductBuilder;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.StringLength;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 5 C4 — {@code Sku} / {@code Product} 紧凑构造器校验 property(tasks §3)。
 *
 * <p>∀ 合法字段 → 构造成功;∀ 违反任一约束 → DomainException。随机样本逼边界反例。
 */
class ProductConstructionProperties {

    private static BigDecimal money(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    // ----- Sku -----

    /** ∀ 合法字段(name 1-100、price>0、stock≥0、sortOrder 0-99)→ 构造成功。 */
    @Property
    void sku_validFields_constructsSuccessfully(
            @ForAll @AlphaChars @StringLength(min = 1, max = 100) String name,
            @ForAll @LongRange(min = 1, max = 100_000_000) long cents,
            @ForAll @IntRange(min = 0, max = 1_000_000) int stock,
            @ForAll @IntRange(min = 0, max = 99) int sortOrder) {
        Sku sku = new Sku("sku-1", name, Map.of(), money(cents), stock, sortOrder);
        assertThat(sku.name()).isEqualTo(name);
        assertThat(sku.stock()).isEqualTo(stock);
    }

    /** ∀ name 长度 > 100 → DomainException。 */
    @Property
    void sku_nameTooLong_throws(
            @ForAll @AlphaChars @StringLength(min = 101, max = 200) String name) {
        assertThatThrownBy(() -> new Sku("s", name, Map.of(), money(100), 0, 0))
                .isInstanceOf(DomainException.class);
    }

    /** ∀ price ≤ 0 → DomainException。 */
    @Property
    void sku_nonPositivePrice_throws(
            @ForAll @LongRange(min = -100_000_000, max = 0) long cents) {
        assertThatThrownBy(() -> new Sku("s", "三文鱼", Map.of(), money(cents), 0, 0))
                .isInstanceOf(DomainException.class);
    }

    /** ∀ stock < 0 → DomainException。 */
    @Property
    void sku_negativeStock_throws(
            @ForAll @IntRange(min = -1_000_000, max = -1) int stock) {
        assertThatThrownBy(() -> new Sku("s", "三文鱼", Map.of(), money(100), stock, 0))
                .isInstanceOf(DomainException.class);
    }

    // ----- Product(经 ProductBuilder 走紧凑构造器)-----

    /** ∀ 合法 price>0 / stock≥0 → Product 构造成功。 */
    @Property
    void product_validFields_constructsSuccessfully(
            @ForAll @LongRange(min = 1, max = 100_000_000) long cents,
            @ForAll @IntRange(min = 0, max = 1_000_000) int stock) {
        Product p = ProductBuilder.aProduct().withPrice(money(cents)).withStock(stock).build();
        assertThat(p.price()).isEqualByComparingTo(money(cents));
        assertThat(p.stock()).isEqualTo(stock);
    }

    /** ∀ price ≤ 0 → DomainException。 */
    @Property
    void product_nonPositivePrice_throws(
            @ForAll @LongRange(min = -100_000_000, max = 0) long cents) {
        assertThatThrownBy(() -> ProductBuilder.aProduct().withPrice(money(cents)).build())
                .isInstanceOf(DomainException.class);
    }

    /** ∀ stock < 0 → DomainException。 */
    @Property
    void product_negativeStock_throws(
            @ForAll @IntRange(min = -1_000_000, max = -1) int stock) {
        assertThatThrownBy(() -> ProductBuilder.aProduct().withStock(stock).build())
                .isInstanceOf(DomainException.class);
    }
}
