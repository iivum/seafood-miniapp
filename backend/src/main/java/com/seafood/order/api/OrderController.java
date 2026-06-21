package com.seafood.order.api;

import com.seafood.order.api.dto.CartItemResponse;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.api.dto.RefundRequest;
import com.seafood.order.api.dto.RefundResponse;
import com.seafood.order.application.OrderService;
import com.seafood.order.domain.OrderTracking;
import com.seafood.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 订单 API(参见 specs/backend-api §Order lifecycle + 4.2 tracking)。
 *
 * <p>权限:
 * <ul>
 *   <li>POST /api/orders — CUSTOMER(自己) / ADMIN(代下单)</li>
 *   <li>GET  /api/orders — CUSTOMER 强制 own,ADMIN 可查任意 userId</li>
 *   <li>GET  /api/orders/{id}/tracking — CUSTOMER(自己) / ADMIN;Service 层做 own 校验,
 *       失败抛 NotFoundException 防 enumeration</li>
 *   <li>POST /api/orders/{id}/ship — ADMIN only</li>
 *   <li>POST /api/orders/{id}/cancel — 订单所属用户(自己) / ADMIN</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal UserPrincipal me) {
        OrderResponse created = orders.create(me.getId());
        return ResponseEntity.created(URI.create("/api/orders/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public Page<OrderResponse> list(
            @RequestParam(required = false) String userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return orders.list(userId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public OrderResponse get(@PathVariable String id) {
        return orders.list(null, org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().filter(o -> o.id().equals(id)).findFirst()
                .orElseThrow(() -> new com.seafood.shared.error.NotFoundException("订单不存在:" + id));
    }

    /**
     * 路线图 4.2:订单物流查询。PENDING / PAID / CANCELLED 订单的 tracking 字段为 null,
     * 返回 200 + null(不是 404 — 订单存在但没物流);订单不存在 404;非订单主且非 ADMIN 404
     * (防 enumeration)。
     */
    @GetMapping("/{id}/tracking")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public OrderTracking getTracking(@PathVariable String id) {
        return orders.getTracking(id);
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse ship(@PathVariable String id) {
        return orders.ship(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public OrderResponse cancel(@PathVariable String id,
                                @RequestParam(required = false) String reason) {
        return orders.cancel(id, reason);
    }

    /**
     * 路线图 4.7:mp 端申请退款(POST /api/orders/{id}/refund)。鉴权:订单所属用户(自己)
     * 或 ADMIN;Service 层做 own 校验,失败抛 NotFoundException 防 enumeration。
     * 成功返回 201 + RefundResponse(JSON 含 Refund.id,供 mp 端乐观更新本地状态)。
     */
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<RefundResponse> requestRefund(
            @PathVariable String id,
            @Valid @RequestBody RefundRequest body) {
        RefundResponse resp = orders.requestRefund(id, body.amount(), body.reason());
        return ResponseEntity
                .created(URI.create("/api/admin/refunds/" + resp.id()))
                .body(resp);
    }

    // ----- sprint-1-closure 1.4 / 1.5 新增 3 个 customer-side 状态机端点 -----

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public OrderResponse pay(@PathVariable String id) {
        return orders.transition(id, com.seafood.order.domain.OrderAction.PAY);
    }

    @PostMapping("/{id}/confirm-receive")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public OrderResponse confirmReceive(@PathVariable String id) {
        return orders.transition(id, com.seafood.order.domain.OrderAction.CONFIRM_RECEIVE);
    }

    @PostMapping("/{id}/rebuy")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public List<CartItemResponse> rebuy(@PathVariable String id) {
        return orders.rebuy(id);
    }

    @PostMapping("/{id}/remind-ship")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<Void> remindShip(@PathVariable String id) {
        orders.remindShip(id);
        return ResponseEntity.noContent().build();
    }
}
