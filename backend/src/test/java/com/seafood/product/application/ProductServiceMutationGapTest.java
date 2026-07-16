package com.seafood.product.application;

import com.seafood.product.api.dto.ProductRequest;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.domain.Product;
import com.seafood.product.domain.ProductStatus;
import com.seafood.product.domain.Sku;
import com.seafood.product.infra.ProductDocument;
import com.seafood.product.infra.ProductMapper;
import com.seafood.product.infra.ProductRepository;
import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
import com.seafood.testsupport.builders.ProductBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 变异测试缺口补强(Sprint 4 C1 续):专门针对 PIT 报告里 product.application 的存活/无覆盖变异,
 * 覆盖 CSV 导出 + SKU 增改删 + updateStatus + 各方法的 not-found 路径。
 * 目标:把 product.application 变异分从 32% 拉过 product floor(并争取 70%)。
 */
class ProductServiceMutationGapTest {

    private ProductRepository repo;
    private ProductService service;

    @BeforeEach
    void setUp() {
        repo = mock(ProductRepository.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        service = new ProductService(repo, meterRegistry);
    }

    // ============ CSV 导出(exportRecentProductsAsCsv + csvEscape)============

    @Test
    void exportCsv_emitsBomHeaderAndDataRowWithAllColumns() {
        ProductDocument d = docOf("p-1", "三文鱼", "鱼类", new BigDecimal("99.00"), 10, ProductStatus.ACTIVE);
        when(repo.findAll()).thenReturn(List.of(d));

        String csv = service.exportRecentProductsAsCsv();

        assertThat(csv).startsWith("﻿商品ID,名称,分类,价格(元),库存,状态,创建时间,更新时间\n");
        // 非空 + 各非 null 列原样出现(杀 ==null 三元的 negate + csvEscape EmptyObjectReturn)
        assertThat(csv).contains("p-1,三文鱼,鱼类,99.00,10,ACTIVE,");
    }

    @Test
    void exportCsv_quotesFieldWithComma() {
        ProductDocument d = docOf("p-1", "三文,鱼", "鱼类", new BigDecimal("99.00"), 10, ProductStatus.ACTIVE);
        when(repo.findAll()).thenReturn(List.of(d));

        String csv = service.exportRecentProductsAsCsv();

        // 含逗号 → 整字段加双引号包裹(RFC 4180)。杀 csvEscape L201 indexOf(',') 分支
        assertThat(csv).contains("\"三文,鱼\"");
    }

    @Test
    void exportCsv_escapesEmbeddedQuoteAndDoublesIt() {
        ProductDocument d = docOf("p-1", "a\"b", "鱼类", new BigDecimal("99.00"), 10, ProductStatus.ACTIVE);
        when(repo.findAll()).thenReturn(List.of(d));

        String csv = service.exportRecentProductsAsCsv();

        // 内嵌双引号 → "" 转义并整体加引号。杀 indexOf('"') 分支 + replace
        assertThat(csv).contains("\"a\"\"b\"");
    }

    @Test
    void exportCsv_quotesFieldWithNewline() {
        ProductDocument d = docOf("p-1", "a\nb", "鱼类", new BigDecimal("99.00"), 10, ProductStatus.ACTIVE);
        when(repo.findAll()).thenReturn(List.of(d));

        String csv = service.exportRecentProductsAsCsv();

        assertThat(csv).contains("\"a\nb\"");   // 杀 indexOf('\n') 分支
    }

    @Test
    void exportCsv_plainFieldIsNotQuoted() {
        ProductDocument d = docOf("p-1", "三文鱼", "鱼类", new BigDecimal("99.00"), 10, ProductStatus.ACTIVE);
        when(repo.findAll()).thenReturn(List.of(d));

        String csv = service.exportRecentProductsAsCsv();

        // 无特殊字符 → 原样返回,不加引号。杀 csvEscape L203 EmptyObjectReturn(返回 "" 而非原值)
        assertThat(csv).contains(",三文鱼,").doesNotContain("\"三文鱼\"");
    }

    @Test
    void exportCsv_nullOptionalFieldsRenderAsEmptyColumns() {
        ProductDocument d = new ProductDocument();
        d.setId("p-null");
        d.setName("x");
        d.setStock(0);
        d.setCategory(null);
        d.setPrice(null);
        d.setStatus(null);
        d.setCreatedAt(null);
        d.setUpdatedAt(null);
        when(repo.findAll()).thenReturn(List.of(d));

        String csv = service.exportRecentProductsAsCsv();

        // category/price/status/created/updated 全 null → 空列;stock=0 仍输出 0
        assertThat(csv).contains("p-null,x,,,0,,,\n");
    }

    // ============ updateStatus ============

    @Test
    void updateStatus_found_appliesNewStatusAndReturnsNonNull() {
        ProductDocument d = docOf("p-1", "三文鱼", "鱼类", new BigDecimal("99.00"), 10, ProductStatus.ACTIVE);
        when(repo.findById("p-1")).thenReturn(Optional.of(d));
        when(repo.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = service.updateStatus("p-1", ProductStatus.DISCONTINUED);

        assertThat(result).isNotNull();   // 杀 L219 NullReturn
        assertThat(result.status()).isEqualTo(ProductStatus.DISCONTINUED);
    }

    @Test
    void updateStatus_notFound_throwsNotFound() {
        when(repo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus("missing", ProductStatus.ACTIVE))
                .isInstanceOf(NotFoundException.class);   // 杀 L216 lambda NullReturn(否则 NPE)
    }

    // ============ SKU updateSku ============

    @Test
    void updateSku_null_throwsDomain() {
        assertThatThrownBy(() -> service.updateSku("p-1", "sku-0", null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void updateSku_productNotFound_throwsNotFound() {
        when(repo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSku("missing", "sku-0", sku(0)))
                .isInstanceOf(NotFoundException.class);   // 杀 L277 lambda
    }

    @Test
    void updateSku_skuIdNotFound_throwsNotFound() {
        stubProductWithSkus("p-1", List.of(sku(0), sku(1)));

        assertThatThrownBy(() -> service.updateSku("p-1", "sku-不存在", sku(9)))
                .isInstanceOf(NotFoundException.class);   // 杀 L287 idx<0 分支
    }

    @Test
    void updateSku_found_replacesByIdKeepingSortOrder() {
        stubProductWithSkus("p-1", List.of(sku(0), sku(1)));
        when(repo.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        // 改 sku-1(非首个,保持首 SKU 99/100 满足校验),新名"新规格"
        Sku newSku = new Sku("sku-1", "新规格", Map.of(), new BigDecimal("99.00"), 100, 7);
        Product result = service.updateSku("p-1", "sku-1", newSku);

        assertThat(result.skus()).anySatisfy(s -> {
            assertThat(s.id()).isEqualTo("sku-1");
            assertThat(s.name()).isEqualTo("新规格");
            assertThat(s.sortOrder()).as("更新不重排,保留原 sortOrder 1").isEqualTo(1);
        });
    }

    // ============ SKU removeSku ============

    @Test
    void removeSku_productNotFound_throwsNotFound() {
        when(repo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeSku("missing", "sku-0"))
                .isInstanceOf(NotFoundException.class);   // 杀 L304 lambda
    }

    @Test
    void removeSku_skuNotFound_throwsNotFound() {
        stubProductWithSkus("p-1", List.of(sku(0), sku(1)));

        assertThatThrownBy(() -> service.removeSku("p-1", "sku-不存在"))
                .isInstanceOf(NotFoundException.class);   // 杀 L316 !found 分支
    }

    @Test
    void removeSku_found_removesAndReordersSortOrder() {
        stubProductWithSkus("p-1", List.of(sku(0), sku(1), sku(2)));
        when(repo.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = service.removeSku("p-1", "sku-1");

        assertThat(result.skus()).hasSize(2);
        assertThat(result.skus()).extracting(Sku::id).containsExactly("sku-0", "sku-2");
        // 重排:剩余 sortOrder 必须是 0,1(杀 L321/L325 重排逻辑)
        assertThat(result.skus()).extracting(Sku::sortOrder).containsExactly(0, 1);
    }

    // ============ 各方法 not-found lambda(NullReturnVals 杀手)============

    @Test
    void addSku_productNotFound_throwsNotFound() {
        when(repo.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addSku("missing", sku(0)))
                .isInstanceOf(NotFoundException.class);   // 杀 L254 lambda
    }

    @Test
    void replaceSkus_productNotFound_throwsNotFound() {
        when(repo.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.replaceSkus("missing", List.of(sku(0))))
                .isInstanceOf(NotFoundException.class);   // 杀 L239 lambda
    }

    @Test
    void update_productNotFound_throwsNotFound() {
        when(repo.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update("missing", sampleRequest()))
                .isInstanceOf(NotFoundException.class);   // 杀 L64 lambda
    }

    @Test
    void discontinue_productNotFound_throwsNotFound() {
        when(repo.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.discontinue("missing"))
                .isInstanceOf(NotFoundException.class);   // 杀 L92 lambda
    }

    @Test
    void decrementStock_productNotFound_throwsNotFound() {
        when(repo.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.decrementStock("missing", 1))
                .isInstanceOf(NotFoundException.class);   // 杀 L330 lambda
    }

    // ============ get / delete / listAdmin / listPublic 分支 ============

    @Test
    void get_found_returnsNonNullResponseWithId() {
        ProductDocument d = docOf("p-1", "三文鱼", "鱼类", new BigDecimal("99.00"), 10, ProductStatus.ACTIVE);
        when(repo.findById("p-1")).thenReturn(Optional.of(d));

        ProductResponse res = service.get("p-1");

        assertThat(res).isNotNull();   // 杀 L138 NullReturn
        assertThat(res.id()).isEqualTo("p-1");
    }

    @Test
    void delete_existing_callsDeleteById() {
        when(repo.existsById("p-1")).thenReturn(true);

        service.delete("p-1");

        verify(repo).deleteById("p-1");   // 杀 L87 VoidMethodCall(删掉 deleteById 调用)
    }

    @Test
    void listAdmin_withCategory_usesFindByCategoryAndReturnsMapped() {
        Pageable pageable = PageRequest.of(0, 20);
        ProductDocument d = docOf("p-1", "三文鱼", "鱼类", new BigDecimal("99.00"), 10, ProductStatus.ACTIVE);
        Page<ProductDocument> page = new PageImpl<>(List.of(d), pageable, 1);
        when(repo.findByCategory("鱼类", pageable)).thenReturn(page);

        Page<ProductResponse> result = service.listAdmin("鱼类", pageable);

        // 杀 L160 negate(category 非空走 findByCategory 而非 findAll)+ L167 非 null 返回
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo("p-1");
        verify(repo).findByCategory("鱼类", pageable);
        verify(repo, never()).findAll(any(Pageable.class));
    }

    @Test
    void listPublic_withCategory_queriesByCategoryAndActiveStatus_noInMemoryOverride() {
        // fix-category-bad-status-500(2026-07-15):旧版用 findByCategory（无状态
        // 过滤）+ 内存态强制覆写 setStatus(ACTIVE)——这既会让下架商品被谎报成在售，
        // 又在集合里出现非法 status 值时于 document→entity 转换阶段直接抛异常，
        // 整个分类 500。新版查询级过滤 status=ACTIVE，天然排除非 ACTIVE（含非法值）
        // 文档，服务层不再做任何状态覆写。
        Pageable pageable = PageRequest.of(0, 20);
        ProductDocument d = docOf("p-1", "三文鱼", "鱼类", new BigDecimal("99.00"), 10, ProductStatus.ACTIVE);
        Page<ProductDocument> page = new PageImpl<>(List.of(d), pageable, 1);
        when(repo.findByCategoryAndStatus(eq("鱼类"), eq(ProductStatus.ACTIVE), eq(pageable))).thenReturn(page);

        Page<ProductResponse> result = service.listPublic("鱼类", pageable);

        assertThat(result.getContent().get(0).status()).isEqualTo(ProductStatus.ACTIVE);
        verify(repo).findByCategoryAndStatus("鱼类", ProductStatus.ACTIVE, pageable);
        verify(repo, never()).findByCategory(any(String.class), any(Pageable.class));
    }

    // ============ helpers ============

    private ProductRequest sampleRequest() {
        return new ProductRequest("三文鱼", "新鲜", new BigDecimal("99.00"), 10, "鱼类", "http://img");
    }

    private static Sku sku(int i) {
        // 首 SKU price/stock 必须 = 商品默认(99.00 / 100),见 Product.replaceSkus 校验
        return new Sku("sku-" + i, "Sku " + i, Map.of(), new BigDecimal("99.00"), 100, i);
    }

    private void stubProductWithSkus(String id, List<Sku> skus) {
        Product product = ProductBuilder.aProduct().withId(id).withSkus(skus).build();
        when(repo.findById(id)).thenReturn(Optional.of(ProductMapper.toDocument(product)));
    }

    private ProductDocument docOf(String id, String name, String category,
                                  BigDecimal price, int stock, ProductStatus status) {
        ProductDocument d = new ProductDocument();
        d.setId(id);
        d.setName(name);
        d.setPrice(price);
        d.setStock(stock);
        d.setCategory(category);
        d.setStatus(status);
        d.setOnSale(status == ProductStatus.ACTIVE);
        d.setCreatedAt(Instant.parse("2026-06-19T00:00:00Z"));
        d.setUpdatedAt(Instant.parse("2026-06-19T00:00:00Z"));
        return d;
    }
}
