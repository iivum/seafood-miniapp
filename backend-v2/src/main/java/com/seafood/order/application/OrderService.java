package com.seafood.order.application;

import com.seafood.order.api.dto.CheckoutRequest;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.api.dto.OrderSummaryResponse;
import com.seafood.order.domain.Cart;
import com.seafood.order.domain.Order;
import com.seafood.order.domain.OrderItem;
import com.seafood.order.infra.CartMongoRepository;
import com.seafood.order.infra.OrderMongoRepository;
import com.seafood.order.infra.ProductStockPort;
import com.seafood.product.domain.Product;
import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.ErrorCode;
import com.seafood.shared.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderMongoRepository orders;
    private final CartMongoRepository carts;
    private final ProductStockPort productPort;

    public OrderService(OrderMongoRepository orders, CartMongoRepository carts, ProductStockPort productPort) {
        this.orders = orders;
        this.carts = carts;
        this.productPort = productPort;
    }

    @Transactional
    public OrderResponse checkout(String userId, CheckoutRequest req) {
        Cart cart = carts.findById(userId)
            .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION, "购物车为空"));

        List<Cart.CartItem> selected = cart.items().stream()
            .filter(Cart.CartItem::selected)
            .toList();
        if (selected.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION, "未选中任何商品");
        }

        // Load all products in one shot
        Set<String> productIds = selected.stream().map(Cart.CartItem::productId).collect(Collectors.toSet());
        Map<String, Product> productMap = productPort.getAll(List.copyOf(productIds)).stream()
            .collect(Collectors.toMap(Product::id, p -> p));

        List<OrderItem> items = selected.stream()
            .map(ci -> {
                Product p = productMap.get(ci.productId());
                if (p == null) {
                    throw new DomainException(ErrorCode.NOT_FOUND, "商品不存在: " + ci.productId());
                }
                if (p.stock() < ci.quantity()) {
                    throw new DomainException(ErrorCode.CONFLICT,
                        "库存不足: " + p.name() + " 剩 " + p.stock() + ",需 " + ci.quantity());
                }
                return new OrderItem(p.id(), p.name(), p.price(), ci.quantity());
            })
            .toList();

        Order order = Order.create(userId, items, Instant.now());
        Order saved = orders.save(order);

        // clear selected items from cart
        List<Cart.CartItem> remaining = cart.items().stream()
            .filter(ci -> !ci.selected())
            .toList();
        carts.save(new Cart(userId, remaining, Instant.now()));

        return toDetailResponse(saved);
    }

    public Page<OrderSummaryResponse> myOrders(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return orders.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(this::toSummaryResponse);
    }

    public OrderResponse get(String userId, String orderId, boolean isAdmin) {
        Order order = orders.findById(orderId)
            .orElseThrow(() -> new NotFoundException("订单不存在"));
        if (!isAdmin && !order.userId().equals(userId)) {
            throw new DomainException(ErrorCode.FORBIDDEN, "无权查看此订单");
        }
        return toDetailResponse(order);
    }

    public OrderResponse markPaid(String orderId, String paymentRef) {
        Order order = orders.findById(orderId)
            .orElseThrow(() -> new NotFoundException("订单不存在"));
        return toDetailResponse(orders.save(order.markPaid(paymentRef, Instant.now())));
    }

    public OrderResponse cancel(String userId, String orderId, String reason, boolean isAdmin) {
        Order order = orders.findById(orderId)
            .orElseThrow(() -> new NotFoundException("订单不存在"));
        if (!isAdmin && !order.userId().equals(userId)) {
            throw new DomainException(ErrorCode.FORBIDDEN, "无权取消此订单");
        }
        return toDetailResponse(orders.save(order.cancel(reason, Instant.now())));
    }

    public OrderResponse ship(String orderId) {
        Order order = orders.findById(orderId)
            .orElseThrow(() -> new NotFoundException("订单不存在"));
        return toDetailResponse(orders.save(order.ship(Instant.now())));
    }

    private OrderResponse toDetailResponse(Order o) {
        return new OrderResponse(
            o.id(), o.userId(), o.status().name(),
            o.items(), o.totalAmount(),
            o.paymentRef(), o.cancelReason(),
            o.createdAt(), o.updatedAt()
        );
    }

    private OrderSummaryResponse toSummaryResponse(Order o) {
        return new OrderSummaryResponse(
            o.id(), o.status().name(), o.totalAmount(),
            o.items().size(), o.createdAt()
        );
    }
}
