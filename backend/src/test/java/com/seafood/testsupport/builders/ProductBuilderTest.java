package com.seafood.testsupport.builders;

import com.seafood.product.domain.Product;
import com.seafood.product.domain.ProductCategory;
import com.seafood.product.domain.ProductStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductBuilderTest {

    @Test
    void defaultBuild_returnsActiveProduct() {
        Product p = ProductBuilder.aProduct().build();
        assertThat(p.id()).isEqualTo("p-test");
        assertThat(p.name()).isEqualTo("测试商品");
        assertThat(p.price()).isEqualByComparingTo(new BigDecimal("99.00"));
        assertThat(p.stock()).isEqualTo(100);
        assertThat(p.category()).isInstanceOf(ProductCategory.Fish.class);
        assertThat(p.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void withPrice_overridesPrice() {
        Product p = ProductBuilder.aProduct().withPrice(new BigDecimal("288.00")).build();
        assertThat(p.price()).isEqualByComparingTo("288.00");
    }

    @Test
    void withStatus_overridesStatus() {
        Product p = ProductBuilder.aProduct().withStatus(ProductStatus.OUT_OF_STOCK).build();
        assertThat(p.status()).isEqualTo(ProductStatus.OUT_OF_STOCK);
    }

    @Test
    void withStock_overridesStock() {
        Product p = ProductBuilder.aProduct().withStock(0).build();
        assertThat(p.stock()).isZero();
    }
}
