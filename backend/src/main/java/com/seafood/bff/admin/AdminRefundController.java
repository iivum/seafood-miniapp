package com.seafood.bff.admin;

import com.seafood.order.api.dto.RefundResponse;
import com.seafood.order.application.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台退款审核端点(参见 design.md §5.1 admin BFF + specs/admin-batch-operations
 * §Refund lifecycle)。
 *
 * <p>路线图 4.8 / 4.11 — ad-06 退款审核 UI。
 * <ul>
 *   <li>{@code GET  /api/admin/refunds?status=REQUESTED} — 待审/已通过/已拒绝 分页列表</li>
 *   <li>{@code POST /api/admin/refunds/{id}/approve} — 同意</li>
 *   <li>{@code POST /api/admin/refunds/{id}/reject} — 拒绝(带 reason)</li>
 * </ul>
 * ADMIN-only。
 *
 * <p>v2 视觉 5.16 / ArchUnit 修复:BFF 不再依赖 {@code RefundRepository} /
 * {@code RefundDocument} / {@code RefundMapper} — 列表查询走
 * {@link OrderService#listRefunds(String, Pageable)},ArchUnit BFF-边界
 * 与 controllers 禁 *Repository 规则同时通过。
 */
@RestController
@RequestMapping("/api/admin/refunds")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundController {

    private final OrderService orders;

    public AdminRefundController(OrderService orders) {
        this.orders = orders;
    }

    /**
     * 4.11:按状态分页列退款单(ad-06 列表用)。默认按 updatedAt 倒序(最新操作在前)。
     * status 缺省 → 全量;UI 通常传 REQUESTED(待审)/ APPROVED / REJECTED。
     */
    @GetMapping
    public Page<RefundResponse> listByStatus(
            @RequestParam(required = false) String status,
            @org.springframework.data.web.PageableDefault(size = 20) Pageable pageable) {
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        return orders.listRefunds(status, sorted);
    }

    @PostMapping("/{id}/approve")
    public RefundResponse approve(@PathVariable String id) {
        return orders.approveRefund(id);
    }

    @PostMapping("/{id}/reject")
    public RefundResponse reject(@PathVariable String id,
                                 @RequestParam(required = false) String reason) {
        return orders.rejectRefund(id, reason);
    }
}
