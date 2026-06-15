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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
    private MeterRegistry meterRegistry;
    private ProductService service;

    @BeforeEach
    void setUp() {
        repo = mock(ProductRepository.class);
        // OpenSpec PR #3 3.2 — 用 SimpleMeterRegistry(纯 in-memory,无网络/无外部依赖)
        // 代替 Mockito mock,直接用 counter().count() 断言,语义更直白;
        // Micrometer 公开 API 在 native 下也工作,虽然此测试不是 @Tag("native")。
        meterRegistry = new SimpleMeterRegistry();
        service = new ProductService(repo, meterRegistry);
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

    // ===== 路线图 3.1:duplicate =====

    @Test
    void duplicate_appendsCopySuffixAndResetsStock() {
        ProductDocument src = docOf("p1", "三文鱼", new BigDecimal("99.00"), 50, ProductStatus.ACTIVE);
        when(repo.findById("p1")).thenReturn(Optional.of(src));
        when(repo.save(any(ProductDocument.class))).thenAnswer(inv -> {
            ProductDocument d = inv.getArgument(0);
            d.setId("p2");
            return d;
        });

        ProductResponse res = service.duplicate("p1");

        assertThat(res.id()).isEqualTo("p2");
        assertThat(res.name()).isEqualTo("三文鱼 (副本)");
        assertThat(res.stock()).as("库存不复位,防误判").isEqualTo(0);
        assertThat(res.status()).as("强制 ACTIVE,即便原商品是 DISCONTINUED").isEqualTo(ProductStatus.ACTIVE);
        assertThat(res.price()).isEqualByComparingTo("99.00");
    }

    @Test
    void duplicate_fromDiscontinuedSourceStillFlipsToActive() {
        ProductDocument src = docOf("p1", "金枪鱼", new BigDecimal("199.00"), 5, ProductStatus.DISCONTINUED);
        when(repo.findById("p1")).thenReturn(Optional.of(src));
        when(repo.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse res = service.duplicate("p1");
        assertThat(res.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void duplicate_throwsNotFoundForMissingSource() {
        when(repo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.duplicate("missing"))
                .isInstanceOf(NotFoundException.class);
        verify(repo, never()).save(any(ProductDocument.class));
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

    // --- 3.2.1:products.queried 计数器埋点(PR #3)---

    @Test
    void listPublic_incrementsProductsQueriedCounterOncePerMatchedCategory() {
        // 3 个鱼类 + 1 个虾蟹 → 4 个 increment,鱼类 tag 累计 3,虾蟹 tag 累计 1
        List<ProductDocument> docs = List.of(
                docOf("p1", "三文鱼", new BigDecimal("99.00"), 10, ProductStatus.ACTIVE),
                docOf("p2", "金枪鱼", new BigDecimal("199.00"), 5, ProductStatus.ACTIVE),
                docOf("p3", "带鱼",   new BigDecimal("49.00"),  20, ProductStatus.ACTIVE),
                docOf("p4", "对虾",   new BigDecimal("129.00"), 8, ProductStatus.ACTIVE));
        // 第三个 doc 改 category 为"虾蟹"以测试多种 category 累加
        docs.get(3).setCategory("虾蟹");
        Pageable pageable = PageRequest.of(0, 20);
        when(repo.findByStatus(ProductStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(docs, pageable, docs.size()));

        service.listPublic(null, pageable);

        assertThat(meterRegistry.counter("products.queried", "category", "鱼类").count())
                .as("鱼类 3 个商品 → counter += 3")
                .isEqualTo(3.0);
        assertThat(meterRegistry.counter("products.queried", "category", "虾蟹").count())
                .as("虾蟹 1 个商品 → counter += 1")
                .isEqualTo(1.0);
    }

    @Test
    void listPublic_emptyResult_doesNotIncrementCounter() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repo.findByStatus(ProductStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.listPublic(null, pageable);

        // 无结果时不应新建任何 counter(避免空 series 污染 PromQL)
        assertThat(meterRegistry.find("products.queried").counters())
                .as("empty listPublic 不应产生任何 products.queried series")
                .isEmpty();
    }

    @Test
    void listAdmin_doesNotEmitProductsQueriedCounter() {
        // listAdmin 走管理后台路径,不应算入"用户浏览商品"业务计数(避免数据污染)
        List<ProductDocument> docs = List.of(
                docOf("p1", "三文鱼", new BigDecimal("99.00"), 10, ProductStatus.ACTIVE));
        Pageable pageable = PageRequest.of(0, 20);
        when(repo.findAll(pageable))
                .thenReturn(new PageImpl<>(docs, pageable, docs.size()));

        service.listAdmin(null, pageable);

        assertThat(meterRegistry.find("products.queried").counters())
                .as("listAdmin 不算用户侧浏览量,不应埋 products.queried")
                .isEmpty();
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
