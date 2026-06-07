package com.seafood.order.api;

import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.application.OrderService;
import com.seafood.shared.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 订单 API(参见 specs/backend-api §Order lifecycle)。
 *
 * <p>权限:
 * <ul>
 *   <li>POST /api/orders — CUSTOMER(自己) / ADMIN(代下单)</li>
 *   <li>GET  /api/orders — CUSTOMER 强制 own,ADMIN 可查任意 userId</li>
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
}
