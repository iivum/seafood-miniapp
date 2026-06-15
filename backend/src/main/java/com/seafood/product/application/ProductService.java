package com.seafood.product.application;

import com.seafood.product.api.dto.ProductRequest;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.domain.Product;
import com.seafood.product.domain.ProductCategory;
import com.seafood.product.domain.ProductStatus;
import com.seafood.product.domain.Sku;
import com.seafood.product.infra.ProductDocument;
import com.seafood.product.infra.ProductMapper;
import com.seafood.product.infra.ProductRepository;
import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 商品写服务 + 公共读(参见 specs/backend-api §Public product browsing / §Admin product management)。
 *
 * <p>约束:
 * <ul>
 *   <li>所有写操作要求 {@code hasRole('ADMIN')},由 Controller @PreAuthorize 守门</li>
 *   <li>公共读不强制登录,但只返回 status == ACTIVE 的商品</li>
 *   <li>{@link #decrementStock} 是给订单服务用的入口,跨模块唯一接口</li>
 * </ul>
 *
 * <p>OpenSpec setup-observability-stack PR #3 — {@link #listPublic} 成功路径对每个返回的商品
 * 累加 {@code products.queried{category=...}} 计数器,tag value 是分类的中文 displayName
 * (与 {@link ProductResponse#category()} 一致)。category 维度基数 = 5(sealed interface),
 * 满足 design §D5 标签白名单约束(参见 ArchUnit
 * {@code MetricsCardinalityTest})。
 */
@Service
public class ProductService {

    private final ProductRepository repo;
    private final MeterRegistry meterRegistry;

    public ProductService(ProductRepository repo, MeterRegistry meterRegistry) {
        this.repo = repo;
        this.meterRegistry = meterRegistry;
    }

    // ----- 写(ADMIN)-----

    public ProductResponse create(ProductRequest req) {
        Instant now = Instant.now();
        Product p = new Product(
                null, req.name(), req.description(), req.price(), req.stock(),
                ProductCategory.of(req.category()), req.imageUrl(),
                ProductStatus.ACTIVE, now, now);
        ProductDocument saved = repo.save(ProductMapper.toDocument(p));
        return ProductResponse.from(ProductMapper.toDomain(saved));
    }

    public ProductResponse update(String id, ProductRequest req) {
        ProductDocument d = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + id));
        Product current = ProductMapper.toDomain(d);
        Product updated = current.updateBasics(req.name(), req.description(), req.price(), req.imageUrl());
        // stock 走专用方法
        if (req.stock() != current.stock()) {
            int delta = req.stock() - current.stock();
            if (delta < 0) {
                updated = updated.decrementStock(-delta);
            } else {
                updated = new Product(updated.id(), updated.name(), updated.description(),
                        updated.price(), req.stock(), updated.category(),
                        updated.imageUrl(), updated.status(),
                        updated.createdAt(), Instant.now());
            }
        }
        ProductDocument saved = repo.save(ProductMapper.toDocument(updated));
        return ProductResponse.from(ProductMapper.toDomain(saved));
    }

    public void delete(String id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("商品不存在:" + id);
        }
        repo.deleteById(id);
    }

    public ProductResponse discontinue(String id) {
        ProductDocument d = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + id));
        Product current = ProductMapper.toDomain(d);
        Product updated = current.withStatus(ProductStatus.DISCONTINUED);
        ProductDocument saved = repo.save(ProductMapper.toDocument(updated));
        return ProductResponse.from(ProductMapper.toDomain(saved));
    }

    /**
     * 路线图 3.1:复制商品(为 ad-03 表格"复制"按钮 + 3.4 E2E 铺路)。
     *
     * <p>行为:
     * <ul>
     *   <li>新商品 id 由 Mongo 自增(传 null 让 spring-data 分配);</li>
     *   <li>name 追加 " (副本)" 后缀,避免与原商品重名被 findFirstByName 命中;</li>
     *   <li>stock 强制置 0 — 库存不应该随原商品"复制"过来(防误判);</li>
     *   <li>status 强制置 ACTIVE — 不继承 DISCONTINUED(避免新商品天生"已下架");</li>
     *   <li>createdAt / updatedAt 戳 now,非原值 — 审计语义上是新商品。</li>
     * </ul>
     *
     * <p>价格 / 描述 / 图片 / 分类都原样复制 — 用户期望"复制"就是改改名称和库存,其他不动。
     */
    public ProductResponse duplicate(String sourceId) {
        ProductDocument src = repo.findById(sourceId)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + sourceId));
        Product current = ProductMapper.toDomain(src);
        Instant now = Instant.now();
        Product copy = new Product(
                null,                                            // id 留给 Mongo
                current.name() + " (副本)",                       // 改名,避免与原商品重名
                current.description(),
                current.price(),
                0,                                                // 库存不复位(防误判)
                current.category(),
                current.imageUrl(),
                ProductStatus.ACTIVE,                              // 强制上架
                now,                                              // 新建时间
                now);
        ProductDocument saved = repo.save(ProductMapper.toDocument(copy));
        return ProductResponse.from(ProductMapper.toDomain(saved));
    }

    // ----- 公共读 -----

    public ProductResponse get(String id) {
        ProductDocument d = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + id));
        return ProductResponse.from(ProductMapper.toDomain(d));
    }

    public Page<ProductResponse> listPublic(String category, Pageable pageable) {
        Page<ProductDocument> page = (category == null || category.isBlank())
                ? repo.findByStatus(ProductStatus.ACTIVE, pageable)
                : repo.findByCategory(category, pageable)
                        .map(d -> { d.setStatus(ProductStatus.ACTIVE); return d; });
        List<ProductResponse> mapped = page.getContent().stream()
                .map(ProductMapper::toDomain)
                .map(ProductResponse::from)
                .toList();
        // 业务埋点:每个被浏览的商品 +1。tag value 是分类 displayName(5 档 sealed
        // interface,低基数),不用 category filter 维度。无结果时 0 增量,
        // 计数器只在真正被消费时增长,避免 0/全量导致 PromQL `rate()` 分母为零。
        for (ProductResponse r : mapped) {
            meterRegistry.counter("products.queried", "category", r.category()).increment();
        }
        return new PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    public Page<ProductResponse> listAdmin(String category, Pageable pageable) {
        Page<ProductDocument> page = (category == null || category.isBlank())
                ? repo.findAll(pageable)
                : repo.findByCategory(category, pageable);
        List<ProductResponse> mapped = page.getContent().stream()
                .map(ProductMapper::toDomain)
                .map(ProductResponse::from)
                .toList();
        return new PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    /**
     * 3.2 后端商品导出 CSV(同 4.15 订单导出同形):UTF-8 BOM + RFC 4180
     * 转义,8 列(商品ID / 名称 / 分类 / 价格 / 库存 / 状态 / 创建时间 / 更新时间)。
     *
     * <p>注意:本迭代(3.2)采用简化方案 — 全表读 + 内存拼字符串,适合 ≤ 1 万行;
     * 超 1 万行应换 Mongo aggregation pipeline 流式输出(参见 3.5 性能 spike)。
     */
    public String exportRecentProductsAsCsv() {
        List<ProductDocument> all = repo.findAll();
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM:Excel 打开 CSV 不乱码
        sb.append('﻿');
        // 表头
        sb.append("商品ID,名称,分类,价格(元),库存,状态,创建时间,更新时间\n");
        for (ProductDocument d : all) {
            sb.append(csvEscape(d.getId())).append(',')
              .append(csvEscape(d.getName())).append(',')
              .append(csvEscape(d.getCategory() == null ? "" : d.getCategory())).append(',')
              .append(d.getPrice() == null ? "" : d.getPrice().toPlainString()).append(',')
              .append(d.getStock()).append(',')
              .append(csvEscape(d.getStatus() == null ? "" : d.getStatus().name())).append(',')
              .append(d.getCreatedAt() == null ? "" : d.getCreatedAt().toString()).append(',')
              .append(d.getUpdatedAt() == null ? "" : d.getUpdatedAt().toString())
              .append('\n');
        }
        return sb.toString();
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        // RFC 4180:含 , " \r \n 的字段必须用双引号包裹,字段内双引号用 "" 转义
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\r') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    // ----- 跨模块入口(给 OrderService 调)-----

    /**
     * 3.3 ad-03 批量状态变更入口 — 单商品改状态。
     * 校验:商品存在(否则 404 NotFoundException);状态转换合法性由 {@code Product.withStatus()} 保证。
     */
    public Product updateStatus(String productId, ProductStatus newStatus) {
        ProductDocument d = repo.findById(productId)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + productId));
        Product updated = ProductMapper.toDomain(d).withStatus(newStatus);
        ProductDocument saved = repo.save(ProductMapper.toDocument(updated));
        return ProductMapper.toDomain(saved);
    }

    /**
     * 3.8 SKU 列表查询:返回 product 的整张 SKU 列表(空 list = 用默认 SKU)。
     * 嵌入存储,等价于 {@code repo.findById(productId).map(d -> d.getSkus())}。
     */
    public List<Sku> listSkus(String productId) {
        return repo.findById(productId)
                .map(ProductDocument::getSkus)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + productId));
    }

    /**
     * 3.8 SKU 整张替换(ad-04 行内编辑保存入口):删除现有 SKU 列表,写入新列表。
     * 校验在 {@code Product.replaceSkus()} 紧凑构造器(防 price / stock 与第一 SKU 不一致 +
     * 数量 0-50 上限)。
     */
    public Product replaceSkus(String productId, List<Sku> newSkus) {
        ProductDocument d = repo.findById(productId)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + productId));
        Product updated = ProductMapper.toDomain(d).replaceSkus(newSkus);
        ProductDocument saved = repo.save(ProductMapper.toDocument(updated));
        return ProductMapper.toDomain(saved);
    }

    /**
     * 3.8 单 SKU 添加(增量 API,ad-04 表单"添加 1 行"按钮)。
     * 走 replaceSkus 实现,等价合并:旧 SKU + 新 SKU,自动重排 sortOrder。
     */
    public Product addSku(String productId, Sku sku) {
        if (sku == null) {
            throw new DomainException("SKU 不能为 null");
        }
        ProductDocument d = repo.findById(productId)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + productId));
        Product p = ProductMapper.toDomain(d);
        List<Sku> existing = p.skus() == null ? List.of() : p.skus();
        // 新 SKU sortOrder = max(existing.sortOrder) + 1,空 list 时 0
        int nextOrder = existing.stream().mapToInt(Sku::sortOrder).max().orElse(-1) + 1;
        Sku withOrder = new Sku(sku.id(), sku.name(), sku.specs(), sku.price(), sku.stock(), nextOrder);
        List<Sku> merged = new java.util.ArrayList<>(existing);
        merged.add(withOrder);
        // 同步默认 price / stock = 第一 SKU(插入后第一行是新加的)
        // 注:这里第一 SKU 仍是原 p 默认,merged[0] 还是原第一;新 SKU sortOrder 最大在末尾
        // 若想让新加的 SKU 作默认,得改 sortOrder 让它到 0;本批保持 append 语义,Sprint 4 升级
        return replaceSkus(productId, merged);
    }

    /**
     * 3.8 单 SKU 更新(增量 API):同 productId 下,按 id 查找替换。
     * 找不到 SKU 抛 NotFoundException(防 enumeration)。
     */
    public Product updateSku(String productId, String skuId, Sku updated) {
        if (updated == null) {
            throw new DomainException("SKU 不能为 null");
        }
        ProductDocument d = repo.findById(productId)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + productId));
        Product p = ProductMapper.toDomain(d);
        List<Sku> existing = p.skus() == null ? List.of() : p.skus();
        int idx = -1;
        for (int i = 0; i < existing.size(); i++) {
            if (skuId.equals(existing.get(i).id())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            throw new NotFoundException("SKU 不存在:" + skuId);
        }
        // 保持 sortOrder 不变(更新不重排)
        Sku merged = new Sku(updated.id(), updated.name(), updated.specs(),
                updated.price(), updated.stock(), existing.get(idx).sortOrder());
        List<Sku> next = new java.util.ArrayList<>(existing);
        next.set(idx, merged);
        return replaceSkus(productId, next);
    }

    /**
     * 3.8 单 SKU 删除(增量 API):按 id 删除,自动重排 sortOrder(0..N-1)。
     * 注意:删完若空 list,Product.replaceSkus 接受(空 list = 用默认 SKU)。
     */
    public Product removeSku(String productId, String skuId) {
        ProductDocument d = repo.findById(productId)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + productId));
        Product p = ProductMapper.toDomain(d);
        List<Sku> existing = p.skus() == null ? List.of() : p.skus();
        List<Sku> next = new java.util.ArrayList<>();
        boolean found = false;
        for (Sku s : existing) {
            if (skuId.equals(s.id())) {
                found = true;
                continue;
            }
            next.add(s);
        }
        if (!found) {
            throw new NotFoundException("SKU 不存在:" + skuId);
        }
        // 重排 sortOrder(0..N-1)
        List<Sku> reordered = new java.util.ArrayList<>(next.size());
        for (int i = 0; i < next.size(); i++) {
            Sku s = next.get(i);
            reordered.add(new Sku(s.id(), s.name(), s.specs(), s.price(), s.stock(), i));
        }
        return replaceSkus(productId, reordered);
    }

    public Product decrementStock(String productId, int quantity) {
        ProductDocument d = repo.findById(productId)
                .orElseThrow(() -> new NotFoundException("商品不存在:" + productId));
        Product updated = ProductMapper.toDomain(d).decrementStock(quantity);
        ProductDocument saved = repo.save(ProductMapper.toDocument(updated));
        return ProductMapper.toDomain(saved);
    }
}
