package com.seafood.order.api;

import com.seafood.order.api.dto.CheckoutRequest;
import com.seafood.order.api.dto.OrderPageResponse;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.api.dto.OrderSummaryResponse;
import com.seafood.order.application.OrderService;
import com.seafood.shared.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public OrderResponse checkout(
        @AuthenticationPrincipal UserPrincipal me,
        @RequestBody(required = false) CheckoutRequest req
    ) {
        return service.checkout(me.userId(), req != null ? req : new CheckoutRequest(null));
    }

    @GetMapping("/me")
    public OrderPageResponse myOrders(
        @AuthenticationPrincipal UserPrincipal me,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<OrderSummaryResponse> p = service.myOrders(me.userId(), page, Math.min(size, 100));
        return new OrderPageResponse(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @GetMapping("/{id}")
    public OrderResponse get(
        @AuthenticationPrincipal UserPrincipal me,
        @PathVariable String id
    ) {
        boolean isAdmin = me.role().name().equals("ADMIN");
        return service.get(me.userId(), id, isAdmin);
    }

    @PatchMapping("/{id}/cancel")
    public OrderResponse cancel(
        @AuthenticationPrincipal UserPrincipal me,
        @PathVariable String id,
        @RequestParam(required = false, defaultValue = "用户取消") String reason
    ) {
        boolean isAdmin = me.role().name().equals("ADMIN");
        return service.cancel(me.userId(), id, reason, isAdmin);
    }

    @PatchMapping("/{id}/pay")
    public OrderResponse markPaid(
        @PathVariable String id,
        @RequestParam String paymentRef
    ) {
        return service.markPaid(id, paymentRef);
    }
}
