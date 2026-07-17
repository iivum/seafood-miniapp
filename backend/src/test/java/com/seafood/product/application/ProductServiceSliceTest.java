package com.seafood.product.application;

import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.domain.Product;
import com.seafood.product.domain.ProductCategory;
import com.seafood.product.domain.ProductStatus;
import com.seafood.product.infra.ProductDocument;
import com.seafood.product.infra.ProductMapper;
import com.seafood.product.infra.ProductRepository;
import com.seafood.shared.error.NotFoundException;
import com.seafood.testsupport.builders.ProductBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ProductService direct unit test — covers public-list and edge cases.
 * Goal: lift Jacoco global line coverage to 80%+.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceSliceTest {

    @Mock private ProductRepository productRepository;
    @Mock private MeterRegistry meterRegistry;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString(), any(String[].class)))
            .thenReturn(org.mockito.Mockito.mock(Counter.class));
        productService = new ProductService(productRepository, meterRegistry);
    }

    @Test
    void listPublic_nullCategory_queriesByStatus() {
        Product p = ProductBuilder.aProduct().withId("p-1").build();
        Page<ProductDocument> page = new PageImpl<>(
            List.of(ProductMapper.toDocument(p)),
            PageRequest.of(0, 20), 1);
        when(productRepository.findByStatus(ProductStatus.ACTIVE, Pageable.unpaged()))
            .thenReturn(page);

        var resp = productService.listPublic(null, Pageable.unpaged());

        assertThat(resp.getContent()).hasSize(1);
        assertThat(resp.getContent().get(0).id()).isEqualTo("p-1");
    }

    @Test
    void listPublic_nonNullCategory_queriesByCategoryAndActiveStatus() {
        // fix-category-bad-status-500:公共分类浏览必须查询级过滤 status=ACTIVE
        // （findByCategoryAndStatus），不能再用 findByCategory + 内存态覆写。
        Product p = ProductBuilder.aProduct().withId("p-fish")
            .withCategory(new ProductCategory.Fish()).build();
        Page<ProductDocument> page = new PageImpl<>(
            List.of(ProductMapper.toDocument(p)),
            PageRequest.of(0, 20), 1);
        when(productRepository.findByCategoryAndStatus("鱼类", ProductStatus.ACTIVE, Pageable.unpaged()))
            .thenReturn(page);

        var resp = productService.listPublic("鱼类", Pageable.unpaged());

        assertThat(resp.getContent()).hasSize(1);
    }

    @Test
    void get_productNotFound_throwsNotFound() {
        when(productRepository.findById("p-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.get("p-missing"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_productNotFound_throwsNotFound() {
        when(productRepository.existsById("p-missing")).thenReturn(false);

        assertThatThrownBy(() -> productService.delete("p-missing"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listSkus_productNotFound_throwsNotFound() {
        when(productRepository.findById("p-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.listSkus("p-missing"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void decrementStock_insufficientStock_throwsDomainException() {
        // decrementStock uses Product.decrementStock, which throws when stock < quantity.
        // Test via stubbing productRepository to return a doc with low stock.
        Product p = ProductBuilder.aProduct().withId("p-low").withStock(3).build();
        when(productRepository.findById("p-low"))
            .thenReturn(Optional.of(ProductMapper.toDocument(p)));

        assertThatThrownBy(() -> productService.decrementStock("p-low", 10))
            .isInstanceOf(com.seafood.shared.error.DomainException.class);
    }
}
