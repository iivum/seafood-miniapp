package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.BatchStatusRequest;
import com.seafood.bff.admin.dto.BatchStatusResponse;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.application.ProductService;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.error.DomainException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台商品操作端点(参见 design.md §5.1 admin BFF + specs/backend-api §Admin product operations)。
 *
 * <p>目前只承载 3.1 duplicate;后续 Sprint 2/3 的 export(3.2)、upload(3.6)等也走这里(均
 * ADMIN-only,与 bff/admin/AdminBffController 路径前缀 / 角色限制一致)。
 *
 * <p>商品 CRUD 基础操作仍由 {@code /api/products/...} 承载(同进程同模块,通过
 * @PreAuthorize 区分角色),这里只放 admin 专属操作。
 */
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final ProductService products;

    public AdminProductController(ProductService products) {
        this.products = products;
    }

    /**
     * 路线图 3.1:复制商品 — ad-03 表格行的"复制"按钮 + 3.4 E2E「筛选 → duplicate」基础。
     *
     * <p>复制语义见 {@link ProductService#duplicate(String)}:新 id / name +" (副本)"/ stock=0 /
     * status=ACTIVE / 新时间戳;其他字段原样复制。
     *
     * <p>返回 201 + Location 头指向新商品(与 create 同形,便于前端 Router 跳详情)。
     */
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ProductResponse> duplicate(@PathVariable String id) {
        ProductResponse created = products.duplicate(id);
        return ResponseEntity
                .created(URI.create("/api/admin/products/" + created.id()))
                .body(created);
    }

    /**
     * 3.2 商品导出 CSV — ad-03 表格"导出"按钮。返回 text/csv;charset=UTF-8 + Content-Disposition: attachment。
     * 8 列(商品ID / 名称 / 分类 / 价格 / 库存 / 状态 / 创建时间 / 更新时间),UTF-8 BOM 防 Excel 乱码。
     * 同 4.15 订单导出同形;BOM 在 ProductService.exportRecentProductsAsCsv 已拼好,前端 BlobReader 直接读。
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        String csv = products.exportRecentProductsAsCsv();
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"products.csv\"")
                .contentLength(body.length)
                .body(body);
    }

    /**
     * 3.3 批量状态变更(ad-03 DataTable "批量上架/下架"按钮)。
     *
     * <p>逐商品处理 + 部分失败(同 4.13 batchShip 模式):HTTP 200 + 业务结果
     * (successCount / failedCount / failed 列表),UI 提示用户;不返 207 Multi-Status
     * (前端 axios 处理更简单)。
     *
     * <p>校验:ids 非空 / status 非空 / 状态转换合法性走 {@code Product.withStatus()}
     * 命名方法(同状态转换拒 409)。
     */
    @PostMapping("/batch-status")
    public ResponseEntity<BatchStatusResponse> batchStatus(@RequestBody BatchStatusRequest body) {
        if (body == null || body.ids() == null || body.ids().isEmpty()) {
            throw new DomainException("ids 不能为空");
        }
        if (body.status() == null) {
            throw new DomainException("status 不能为空");
        }
        if (body.ids().size() > 200) {
            throw new DomainException("单次最多 200 个商品");
        }
        List<String> success = new ArrayList<>();
        List<BatchStatusResponse.FailedItem> failed = new ArrayList<>();
        for (String id : body.ids()) {
            try {
                products.updateStatus(id, body.status());
                success.add(id);
            } catch (NotFoundException e) {
                failed.add(new BatchStatusResponse.FailedItem(id, "商品不存在"));
            } catch (DomainException e) {
                failed.add(new BatchStatusResponse.FailedItem(id, e.getMessage()));
            }
        }
        return ResponseEntity.ok(BatchStatusResponse.of(success, failed));
    }
}
