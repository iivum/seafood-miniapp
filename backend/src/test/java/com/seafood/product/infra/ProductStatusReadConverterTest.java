package com.seafood.product.infra;

import com.seafood.product.domain.ProductStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * fix-category-bad-status-500 task 1.3:非法 status 字符串必须归一为 DISCONTINUED
 * 而不是抛异常(Spring Data MongoDB 默认 enum 转换器遇到未知值会抛
 * IllegalArgumentException,发生在 document→entity 转换阶段，业务代码来不及兜底）。
 */
class ProductStatusReadConverterTest {

    private final ProductStatusReadConverter converter = new ProductStatusReadConverter();

    @Test
    void convert_legalValue_returnsMatchingEnum() {
        assertThat(converter.convert("ACTIVE")).isEqualTo(ProductStatus.ACTIVE);
        assertThat(converter.convert("OUT_OF_STOCK")).isEqualTo(ProductStatus.OUT_OF_STOCK);
        assertThat(converter.convert("DISCONTINUED")).isEqualTo(ProductStatus.DISCONTINUED);
    }

    @Test
    void convert_illegalValue_fallsBackToDiscontinued_insteadOfThrowing() {
        assertThat(converter.convert("INACTIVE")).isEqualTo(ProductStatus.DISCONTINUED);
    }

    @Test
    void convert_emptyOrGarbageValue_fallsBackToDiscontinued() {
        assertThat(converter.convert("")).isEqualTo(ProductStatus.DISCONTINUED);
        assertThat(converter.convert("garbage-123")).isEqualTo(ProductStatus.DISCONTINUED);
    }
}
