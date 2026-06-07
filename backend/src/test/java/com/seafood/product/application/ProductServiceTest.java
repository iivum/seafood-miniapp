package com.seafood.product.application;

import com.seafood.product.api.dto.ProductRequest;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.domain.Product;
import com.seafood.product.domain.ProductCategory;
import com.seafood.product.domain.ProductStatus;
import com.seafood.product.infra.ProductDocument;
import com.seafood.product.infra.ProductMapper;
import com.seafood.product.infra.ProductRepository;
import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private ProductRepository repo;
    private ProductService service;

    @BeforeEach
    void setUp() {
        repo = mock(ProductRepository.class);
        service = new ProductService(repo);
    }

    private ProductRequest sampleRequest() {
        return new ProductRequest("三文鱼", "新鲜", new BigDecimal("99.00"), 10, "鱼类", "http://img");
    }

    @Test
    void create_savesAsActiveAndReturnsResponse() {
        when(repo.save(any(ProductDocument.class))).thenAnswer(inv -> {
            ProductDocument d = inv.getArgument(0);
            d.setId("p1");
            return d;
        });

        ProductResponse res = service.create(sampleRequest());

        ArgumentCaptor<ProductDocument> cap = ArgumentCaptor.forClass(ProductDocument.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(cap.getValue().isOnSale()).isTrue();
        assertThat(res.id()).isEqualTo("p1");
        assertThat(res.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void update_appliesNewFieldsAndStamps() {
        ProductDocument existing = docOf("p1", "三文鱼", new BigDecimal("99.00"), 10, ProductStatus.ACTIVE);
        when(repo.findById("p1")).thenReturn(Optional.of(existing));
        when(repo.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest req = new ProductRequest("金枪鱼", "新鲜金枪鱼", new BigDecimal("199.00"), 10, "鱼类", "http://img2");
        ProductResponse res = service.update("p1", req);

        assertThat(res.name()).isEqualTo("金枪鱼");
        assertThat(res.price()).isEqualByComparingTo("199.00");
    }

    @Test
    void update_reducesStockViaDomainRule() {
        ProductDocument existing = docOf("p1", "三文鱼", new BigDecimal("99.00"), 10, ProductStatus.ACTIVE);
        when(repo.findById("p1")).thenReturn(Optional.of(existing));
        when(repo.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest req = new ProductRequest("三文鱼", null, new BigDecimal("99.00"), 3, "鱼类", null);
        ProductResponse res = service.update("p1", req);

        assertThat(res.stock()).isEqualTo(3);
    }

    @Test
    void update_increaseStockBypassesDecrementCheck() {
        // 增量路径(2 → 10)不会触发 decrementStock 的不足检查,@PositiveOrZero 限定
        // 下 update 路径无法表达"扣到负数"。该场景由 decrementStock_rejectsInsufficient 覆盖。
        ProductDocument existing = docOf("p1", "三文鱼", new BigDecimal("99.00"), 2, ProductStatus.ACTIVE);
        when(repo.findById("p1")).thenReturn(Optional.of(existing));
        when(repo.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest req = new ProductRequest("三文鱼", null, new BigDecimal("99.00"), 10, "鱼类", null);
        ProductResponse res = service.update("p1", req);

        assertThat(res.stock()).isEqualTo(10);
    }

    @Test
    void delete_throwsWhenMissing() {
        when(repo.existsById("missing")).thenReturn(false);
        assertThatThrownBy(() -> service.delete("missing")).isInstanceOf(NotFoundException.class);
        verify(repo, never()).deleteById(any());
    }

    @Test
    void decrementStock_isTheCrossModuleEntry() {
        ProductDocument existing = docOf("p1", "三文鱼", new BigDecimal("99.00"), 5, ProductStatus.ACTIVE);
        when(repo.findById("p1")).thenReturn(Optional.of(existing));
        when(repo.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = service.decrementStock("p1", 2);
        assertThat(updated.stock()).isEqualTo(3);
    }

    @Test
    void decrementStock_rejectsInsufficient() {
        ProductDocument existing = docOf("p1", "三文鱼", new BigDecimal("99.00"), 1, ProductStatus.ACTIVE);
        when(repo.findById("p1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.decrementStock("p1", 5))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void get_throwsOnMissing() {
        when(repo.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("missing")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void discontinue_flipsStatus() {
        ProductDocument existing = docOf("p1", "三文鱼", new BigDecimal("99.00"), 5, ProductStatus.ACTIVE);
        when(repo.findById("p1")).thenReturn(Optional.of(existing));
        when(repo.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse res = service.discontinue("p1");
        assertThat(res.status()).isEqualTo(ProductStatus.DISCONTINUED);
    }

    @Test
    void productMapper_roundTripPreservesCategory() {
        Product p = new Product("p1", "三文鱼", "x", new BigDecimal("1"), 0,
                new ProductCategory.Shrimp(), null, ProductStatus.ACTIVE,
                Instant.now(), Instant.now());
        ProductDocument d = ProductMapper.toDocument(p);
        Product back = ProductMapper.toDomain(d);
        assertThat(back.category()).isInstanceOf(ProductCategory.Shrimp.class);
    }

    // ----- helpers -----

    private ProductDocument docOf(String id, String name, BigDecimal price, int stock, ProductStatus status) {
        ProductDocument d = new ProductDocument();
        d.setId(id);
        d.setName(name);
        d.setPrice(price);
        d.setStock(stock);
        d.setCategory("鱼类");
        d.setStatus(status);
        d.setOnSale(status == ProductStatus.ACTIVE);
        d.setCreatedAt(Instant.now());
        d.setUpdatedAt(Instant.now());
        return d;
    }
}
