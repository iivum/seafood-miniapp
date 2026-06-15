package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.BatchShipRequest;
import com.seafood.bff.admin.dto.BatchShipResponse;
import com.seafood.order.application.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台订单操作端点(参见 design.md §5.1 admin BFF + specs/admin-batch-operations)。
 *
 * <p>路线图 4.13 — ad-05 订单列表「批量发货」按钮 + 4.15「导出 CSV」按钮。
 * 与 3.1 duplicate / 4.8 refund 审核风格一致:@PreAuthorize ADMIN + 业务全走 OrderService。
 */
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orders;

    public AdminOrderController(OrderService orders) {
        this.orders = orders;
    }

    /**
     * 路线图 4.13:批量发货。{@code orderIds} 1..50 个;{@code carrier} / {@code trackingNumber}
     * 可选(同时填或同时留空 — 半填报"global"失败项),不填时只把状态 PAID → SHIPPED。
     *
     * <p>返回 200(注意:即便有单失败也返 200 — 4.13 设计就是 partial success;UI 看
     * successCount / failedCount 决定弹哪种 toast)。HTTP 200 + 业务结果的双层
     * 语义,符合"逐单处理 + 失败跳过"策略(若用 207 Multi-Status 也可,但前端
     * 通常更熟悉 200 + JSON)。
     */
    @PostMapping("/batch-ship")
    public BatchShipResponse batchShip(@Valid @RequestBody BatchShipRequest body) {
        return orders.batchShip(body.orderIds(), body.carrier(), body.trackingNumber());
    }

    /**
     * 路线图 4.15:导出订单 CSV(ad-05 订单列表「导出」按钮)。
     *
     * <p>策略:复用 {@link OrderService#findRecent(int)} 取最近 500 单(单仓单 seller
     * 阶段够用,生产应换 Mongo aggregation pipeline — 留 TODO,与 findRecent 注释
     * 同步)。500 条数据走内存拼 String 即可,无需 StreamingResponseBody。
     *
     * <p>格式:UTF-8 BOM + RFC 4180 CSV(逗号分隔 + 双引号转义包含逗号/引号/换行的
     * 字段)。Excel 打开 UTF-8 BOM 自动识别中文不乱码;Linux 工具(cat / less)看
     * 头 3 字节为 BOM 但不影响。
     *
     * <p>列:订单号 / 用户 ID / 金额(元) / 状态 / 取消原因(仅 CANCELLED) /
     * 创建时间(ISO-8601 UTC) / 更新时间(ISO-8601 UTC)。物流不在 CSV 内(信息
     * 密度低,Excel 也难展示嵌套结构)— ad-06 详情页看。
     */
    @GetMapping("/export")
    public ResponseEntity<String> exportCsv() {
        String csv = orders.exportRecentOrdersAsCsv(500);
        String filename = "orders-" + java.time.LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body("﻿" + csv);
    }

    /**
     * 路线图 4.14:打印拣货单(ad-05 订单详情/列表"打印拣货单"按钮)。
     *
     * <p>本期实现为可打印 HTML 兜底(Content-Type: text/html; Content-Disposition
     * attachment; 浏览器 Ctrl+P → 另存为 PDF,或 admin 端直接打印)。选择 HTML 而非
     * PDF 库的原因:
     * <ul>
     *   <li>OpenPDF 7.x(AGPL 之外最友好)在 GraalVM Native 反射 metadata 复杂
     *       (font / image / resource 全要 agent 收集),初次落地需多轮 nativeTest
     *       调 — design §5.2 当前 BFF 不缓存,不应在 PDF 链路阻塞主进程 native 构建;</li>
     *   <li>HTML 兜底 0 依赖、0 反射、native binary 体积不变(几十 KB 字符串),</li>
     *   <li>admin 浏览器端 Ctrl+P 选「另存为 PDF」生成的 PDF 与 iText 生成的几乎无差
     *       (现代浏览器 print-to-PDF 体验稳定)。</li>
     * </ul>
     * <p>Sprint 后 4.14 重做时,接 OpenPDF 7.x(AGPL-MIT 双协议,商业友好)+ Flying
     * Saucer(HTML→PDF)或纯 OpenPDF;此处留 TODO。
     */
    @GetMapping("/{id}/print-picklist")
    public ResponseEntity<String> printPicklist(@PathVariable String id) {
        String html = orders.renderPicklistHtml(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"picklist-" + id + ".html\"")
                .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
                .body(html);
    }
}
