package com.seafood.product.application;

import com.seafood.product.domain.Sku;
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

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceSkuSliceTest {

    @Mock private ProductRepository productRepository;
    @Mock private MeterRegistry meterRegistry;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<String[]>any()))
            .thenReturn(org.mockito.Mockito.mock(Counter.class));
        productService = new ProductService(productRepository, meterRegistry);
    }

    private static Sku sku(int i) {
        // Sku price + stock must equal product default (99.00, 100) per Product validation
        return new Sku("sku-" + i, "Sku " + i, java.util.Map.of(),
            new java.math.BigDecimal("99.00"), 100, i);
    }

    @Test
    void listSkus_productNotFound_throwsNotFound() {
        when(productRepository.findById("p-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.listSkus("p-missing"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listSkus_productFound_returnsSkus() {
        var product = ProductBuilder.aProduct().withId("p-1")
            .withSkus(List.of(sku(0), sku(1))).build();
        when(productRepository.findById("p-1"))
            .thenReturn(Optional.of(ProductMapper.toDocument(product)));

        var skus = productService.listSkus("p-1");

        assertThat(skus).hasSize(2);
        assertThat(skus.get(0).id()).isEqualTo("sku-0");
    }

    @Test
    void replaceSkus_validCount_succeeds() {
        var product = ProductBuilder.aProduct().withId("p-1")
            .withSkus(List.of(sku(0))).build();
        when(productRepository.findById("p-1"))
            .thenReturn(Optional.of(ProductMapper.toDocument(product)));
        when(productRepository.save(org.mockito.ArgumentMatchers.any(ProductDocument.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var updated = productService.replaceSkus("p-1", List.of(sku(0), sku(1), sku(2)));

        assertThat(updated.skus()).hasSize(3);
    }

    @Test
    void replaceSkus_tooMany_throwsDomainException() {
        var product = ProductBuilder.aProduct().withId("p-1")
            .withSkus(List.of()).build();
        when(productRepository.findById("p-1"))
            .thenReturn(Optional.of(ProductMapper.toDocument(product)));

        var tooMany = IntStream.range(0, 51)
            .mapToObj(ProductServiceSkuSliceTest::sku)
            .toList();

        assertThatThrownBy(() -> productService.replaceSkus("p-1", tooMany))
            .isInstanceOf(com.seafood.shared.error.DomainException.class);
    }

    @Test
    void addSku_appendedToExisting() {
        var product = ProductBuilder.aProduct().withId("p-1")
            .withSkus(List.of(sku(0))).build();
        when(productRepository.findById("p-1"))
            .thenReturn(Optional.of(ProductMapper.toDocument(product)));
        when(productRepository.save(org.mockito.ArgumentMatchers.any(ProductDocument.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var updated = productService.addSku("p-1", sku(99));

        assertThat(updated.skus()).hasSize(2);
    }
}
