package com.seafood.order.application;

import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.domain.Cart;
import com.seafood.order.domain.CartItem;
import com.seafood.order.domain.Order;
import com.seafood.order.domain.OrderItem;
import com.seafood.order.domain.OrderStatus;
import com.seafood.order.infra.CartRepository;
import com.seafood.order.infra.OrderDocument;
import com.seafood.order.infra.OrderMapper;
import com.seafood.order.infra.OrderRepository;
import com.seafood.product.domain.Product;
import com.seafood.product.infra.ProductRepository;
import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.Role;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单服务(参见 specs/backend-api §Order lifecycle)。
 *
 * <p>关键路径:
 * <ul>
 *   <li>create:读 cart → 拉商品最新价/库存 → 快照 → 调 ProductService.decrementStock
 *       (扣减失败回滚整个下单,见 catch)→ 持久化 → 清空 cart</li>
 *   <li>list:调用方角色决定 userId 过滤 — CUSTOMER 看自己,ADMIN 看全部</li>
 *   <li>ship/cancel:状态机集中规则,任何越界抛 DomainException</li>
 * </ul>
 *
 * <p>OpenSpec setup-observability-stack PR #3 — 业务计数器埋点
 * (design §D5 + specs/metrics-export §Business counters):
 * <ul>
 *   <li>{@code orders.created{paymentMethod}} — 下单成功时累加,tag 为支付渠道</li>
 *   <li>{@code orders.cancelled{reason}} — 取消成功时累加,tag 为取消原因</li>
 *   <li>{@code orders.paid{paymentMethod,amountBucket}} — 支付成功时累加,amount bucket
 *       由 {@link OrderMetrics#bucketize} 计算(design §ADR-OQ3 几何 4 档)</li>
 * </ul>
 * tag 全部为低基数字符串(wechat/admin/unknown,user/timeout/admin,lt100/100to500/500to2000/gte2000),
 * 满足 ArchUnit {@code MetricsCardinalityTest} 约束(参见 design §D5)。
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final CartRepository carts;
    private final ProductRepository products;
    private final MeterRegistry meterRegistry;

    public OrderService(OrderRepository orders,
                        CartRepository carts,
                        ProductRepository products,
                        MeterRegistry meterRegistry) {
        this.orders = orders;
        this.carts = carts;
        this.products = products;
        this.meterRegistry = meterRegistry;
    }

    // ----- create -----

    public OrderResponse create(String userId) {
        return create(userId, "wechat");
    }

    /**
     * @param paymentMethod 支付渠道 — 留 metric tag 用。当前单渠道(微信小程序),暂不持久化到
     *                      Order document(Sprint 3 接入真实支付时再加 {@code paymentMethod}
     *                      字段 + Mongo migration);只用于埋 {@code orders.created} 计数。
     */
    public OrderResponse create(String userId, String paymentMethod) {
        Cart cart = carts.findById(userId)
                .map(d -> new com.seafood.order.domain.Cart(d.getUserId(), d.getItems(), d.getUpdatedAt()))
                .orElseThrow(() -> new DomainException("购物车为空"));
        cart.requireNonEmptySelected();

        // 1) 拉所有商品 → 快照 + 校验存在/上架
        List<com.seafood.product.infra.ProductDocument> docs = products.findAllById(
                cart.items().stream().map(c -> c.productId()).toList());
        if (docs.size() != new java.util.HashSet<>(cart.items().stream().map(c -> c.productId()).toList()).size()) {
            throw new DomainException("购物车包含已下架商品");
        }
        java.util.Map<String, com.seafood.product.infra.ProductDocument> byId = new java.util.HashMap<>();
        for (var d : docs) byId.put(d.getId(), d);

        List<OrderItem> items = new ArrayList<>();
        for (CartItem ci : cart.items()) {
            if (!ci.selected()) continue;
            var pd = byId.get(ci.productId());
            if (pd == null) {
                throw new DomainException("商品不存在:" + ci.productId());
            }
            if (pd.getStatus() != com.seafood.product.domain.ProductStatus.ACTIVE) {
                throw new DomainException("商品已下架:" + pd.getName());
            }
            if (pd.getStock() < ci.quantity()) {
                throw new DomainException("库存不足:" + pd.getName() + " (剩余 " + pd.getStock() + ")");
            }
            items.add(new OrderItem(pd.getId(), pd.getName(), pd.getPrice(), ci.quantity()));
        }
        BigDecimal total = items.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2) 扣减库存(失败时记录已扣商品;Mongo 事务在生产应启用 — design §6.1 TODO)
        java.util.List<String> decremented = new ArrayList<>();
        try {
            for (OrderItem it : items) {
                Product p = productDecrementOrThrow(it.productId(), it.quantity());
                decremented.add(p.id());
            }
        } catch (DomainException e) {
            // 暂不回滚:无 Mongo 事务,反向 inc 风险比保留更糟;运维侧 reconcile
            // 后续接事务后改为 throw + 自动回滚
            throw e;
        }

        // 3) 持久化订单
        Instant now = Instant.now();
        Order order = new Order(null, userId, items, total, new OrderStatus.Pending(), null, now, now);
        OrderDocument saved = orders.save(OrderMapper.toDocument(order));

        // 4) 清空 cart
        carts.deleteById(userId);

        // 5) 业务埋点(PR #3 3.3):下单成功(库存已扣 + 订单已落库 + cart 已清)后累加。
        // 失败路径(库存不足/商品下架/购物车空)在 catch + 早 throw 处退出,不递增。
        // paymentMethod tag 显式落在 create(userId, paymentMethod) 重载入口,无值默认
        // "wechat"(本期单渠道);Sprint 3 多支付接入后这里扩展为枚举校验。
        String method = (paymentMethod == null || paymentMethod.isBlank()) ? "wechat" : paymentMethod;
        meterRegistry.counter("orders.created", "paymentMethod", method).increment();

        return OrderResponse.from(OrderMapper.toDomain(saved));
    }

    // ----- list -----

    public Page<OrderResponse> list(String requestedUserId, Pageable pageable) {
        boolean isAdmin = currentRole() == Role.ADMIN;
        String effectiveUserId = isAdmin
                ? (requestedUserId == null || requestedUserId.isBlank() ? null : requestedUserId)
                : currentUserId();
        Page<OrderDocument> page = (isAdmin && effectiveUserId == null)
                ? orders.findAll(pageable)
                : orders.findByUserId(effectiveUserId, pageable);
        List<OrderResponse> mapped = page.getContent().stream()
                .map(OrderMapper::toDomain)
                .map(OrderResponse::from)
                .toList();
        return new PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    // ----- ship / cancel(状态机)-----

    public OrderResponse ship(String orderId) {
        Order o = load(orderId);
        Order next = o.markShipped(Instant.now());
        return OrderResponse.from(persistAndReturn(next));
    }

    public OrderResponse cancel(String orderId, String reason) {
        Order o = load(orderId);
        Order next = o.cancel(reason, Instant.now());
        OrderResponse resp = OrderResponse.from(persistAndReturn(next));

        // 业务埋点(PR #3 3.4):取消成功时累加。reason 规范化到低基数白名单
        // (user / timeout / admin)— 其他输入(空 / 随意字符串)归到 "other",防止
        // 用户/攻击者在 reason 里塞高基数字符串污染 PromQL 时间序列。
        String tag = normalizeCancelReason(reason);
        meterRegistry.counter("orders.cancelled", "reason", tag).increment();
        return resp;
    }

    public OrderResponse markPaid(String orderId) {
        Order o = load(orderId);
        Order next = o.markPaid(Instant.now());
        OrderResponse resp = OrderResponse.from(persistAndReturn(next));

        // 业务埋点(PR #3 3.5):支付成功时累加。paymentMethod tag 暂固定 "wechat"
        // (本期单渠道,订单创建时无 paymentMethod 字段 — Sprint 3 接入真实支付再
        // 扩展);amountBucket 由 OrderMetrics.bucketize(design §ADR-OQ3 几何 4 档)
        // 计算 — 严格低基数标签白名单。
        String amountBucket = OrderMetrics.bucketize(next.totalAmount());
        meterRegistry.counter("orders.paid",
                        "paymentMethod", "wechat",
                        "amountBucket", amountBucket)
                .increment();
        return resp;
    }

    /**
     * 把 {@code reason} 字符串规范化到低基数白名单 4 档。空白或不在白名单的归 "other",
     * 防止任意字符串污染 PromQL series 数量。
     */
    private static String normalizeCancelReason(String reason) {
        if (reason == null) return "other";
        String r = reason.trim();
        if (r.isEmpty()) return "other";
        if (r.equalsIgnoreCase("user")) return "user";
        if (r.equalsIgnoreCase("timeout")) return "timeout";
        if (r.equalsIgnoreCase("admin")) return "admin";
        return "other";
    }

    // ----- 跨模块只读(BFF 用)-----

    public OrderResponse get(String orderId) {
        return OrderResponse.from(load(orderId));
    }

    public long countCreatedSince(Instant from) {
        return orders.countByCreatedAtGreaterThanEqual(from);
    }

    /** 用于 BFF topProducts 聚合;Phase 1 拉最近 500 单,生产应换 Mongo aggregation pipeline。 */
    public List<OrderResponse> findRecent(int limit) {
        var docs = orders.findTop500ByOrderByCreatedAtDesc();
        int n = Math.min(limit, docs.size());
        return docs.subList(0, n).stream()
                .map(OrderMapper::toDomain)
                .map(OrderResponse::from)
                .toList();
    }

    // ----- helpers -----

    private Order load(String orderId) {
        return orders.findById(orderId)
                .map(OrderMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("订单不存在:" + orderId));
    }

    private Order persistAndReturn(Order o) {
        OrderDocument saved = orders.save(OrderMapper.toDocument(o));
        return OrderMapper.toDomain(saved);
    }

    private Product productDecrementOrThrow(String productId, int qty) {
        var doc = products.findById(productId)
                .orElseThrow(() -> new DomainException("商品不存在:" + productId));
        int newStock = doc.getStock() - qty;
        if (newStock < 0) {
            throw new DomainException("库存不足:" + doc.getName());
        }
        doc.setStock(newStock);
        if (newStock == 0 && doc.getStatus() == com.seafood.product.domain.ProductStatus.ACTIVE) {
            doc.setStatus(com.seafood.product.domain.ProductStatus.OUT_OF_STOCK);
        }
        products.save(doc);
        return new Product(
                doc.getId(), doc.getName(), doc.getDescription(), doc.getPrice(), doc.getStock(),
                com.seafood.product.domain.ProductCategory.of(doc.getCategory()),
                doc.getImageUrl(), doc.getStatus(), doc.getCreatedAt(), Instant.now());
    }

    private int cartItemQty(Cart cart, String productId) {
        return cart.items().stream()
                .filter(i -> i.productId().equals(productId))
                .mapToInt(CartItem::quantity)
                .findFirst()
                .orElse(0);
    }

    private static Role currentRole() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(s -> s.startsWith("ROLE_"))
                .map(s -> s.substring(5))
                .map(Role::valueOf)
                .findFirst()
                .orElse(null);
    }

    private static String currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof com.seafood.shared.security.UserPrincipal up) {
            return up.getId();
        }
        return auth.getName();
    }
}
