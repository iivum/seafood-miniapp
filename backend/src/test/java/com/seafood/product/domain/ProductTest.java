package com.seafood.product.domain;

import com.seafood.shared.error.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private final ProductCategory fish = new ProductCategory.Fish();
    private final Instant now = Instant.parse("2026-06-01T00:00:00Z");

    private Product sample(int stock) {
        return new Product("p1", "三文鱼", "新鲜", new BigDecimal("99.00"),
                stock, fish, "http://img", ProductStatus.ACTIVE, now, now);
    }

    @Test
    void create_rejectsBlankName() {
        assertThatThrownBy(() -> new Product("p1", "  ", "x", new BigDecimal("1"), 0, fish, null, ProductStatus.ACTIVE, now, now))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("名称");
    }

    @Test
    void create_rejectsNonPositivePrice() {
        assertThatThrownBy(() -> new Product("p1", "x", "x", BigDecimal.ZERO, 0, fish, null, ProductStatus.ACTIVE, now, now))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("价格");
    }

    @Test
    void create_rejectsNegativeStock() {
        assertThatThrownBy(() -> new Product("p1", "x", "x", new BigDecimal("1"), -1, fish, null, ProductStatus.ACTIVE, now, now))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("库存");
    }

    @Test
    void create_rejectsNullCategory() {
        assertThatThrownBy(() -> new Product("p1", "x", "x", new BigDecimal("1"), 0, null, null, ProductStatus.ACTIVE, now, now))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("分类");
    }

    @Test
    void decrementStock_reducesAndStampsUpdatedAt() {
        Product before = sample(10);
        Product after = before.decrementStock(3);
        assertThat(after.stock()).isEqualTo(7);
        assertThat(after.updatedAt()).isAfter(now);
    }

    @Test
    void decrementStock_rejectsInsufficient() {
        Product p = sample(2);
        assertThatThrownBy(() -> p.decrementStock(5))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("库存不足");
    }

    @Test
    void decrementStock_rejectsNonPositive() {
        Product p = sample(5);
        assertThatThrownBy(() -> p.decrementStock(0))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> p.decrementStock(-1))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void withStatus_swapsStatusAndStamps() {
        Product p = sample(5).withStatus(ProductStatus.DISCONTINUED);
        assertThat(p.status()).isEqualTo(ProductStatus.DISCONTINUED);
        assertThat(p.updatedAt()).isAfter(now);
    }

    @Test
    void updateBasics_preservesIdAndStock() {
        Product p = sample(5).updateBasics("金枪鱼", "新鲜金枪鱼", new BigDecimal("199.00"), "http://img2");
        assertThat(p.id()).isEqualTo("p1");
        assertThat(p.name()).isEqualTo("金枪鱼");
        assertThat(p.stock()).isEqualTo(5);
        assertThat(p.price()).isEqualByComparingTo("199.00");
    }

    @Test
    void productCategory_of_mapsAllValid() {
        assertThat(ProductCategory.of("鱼类")).isInstanceOf(ProductCategory.Fish.class);
        assertThat(ProductCategory.of("虾蟹")).isInstanceOf(ProductCategory.Shrimp.class);
        assertThat(ProductCategory.of("贝类")).isInstanceOf(ProductCategory.Shell.class);
        assertThat(ProductCategory.of("软体")).isInstanceOf(ProductCategory.Mollusk.class);
        assertThat(ProductCategory.of("海藻")).isInstanceOf(ProductCategory.Seaweed.class);
    }

    @Test
    void productCategory_of_rejectsUnknown() {
        assertThatThrownBy(() -> ProductCategory.of("不存在的分类"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("未知分类");
    }
}
