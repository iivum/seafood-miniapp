package com.seafood.order.application;

import com.seafood.order.api.dto.CartItemRequest;
import com.seafood.order.api.dto.CartItemResponse;
import com.seafood.order.api.dto.OrderResponse;
import com.seafood.order.api.dto.RefundResponse;
import com.seafood.order.domain.Cart;
import com.seafood.order.domain.CartItem;
import com.seafood.order.domain.Order;
import com.seafood.order.domain.OrderAction;
import com.seafood.order.domain.OrderItem;
import com.seafood.order.domain.OrderStatus;
import com.seafood.order.domain.Refund;
import com.seafood.order.infra.CartRepository;
import com.seafood.order.infra.OrderDocument;
import com.seafood.order.infra.OrderMapper;
import com.seafood.order.infra.OrderRepository;
import com.seafood.order.domain.OrderTracking;
import com.seafood.order.domain.RefundStatus;
import com.seafood.order.infra.RefundDocument;
import com.seafood.order.infra.RefundMapper;
import com.seafood.order.infra.RefundRepository;
import com.seafood.product.domain.Product;
import com.seafood.product.infra.ProductRepository;
import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.Role;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
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
    private final RefundRepository refunds;
    private final MeterRegistry meterRegistry;

    public OrderService(OrderRepository orders,
                        CartRepository carts,
                        ProductRepository products,
                        MeterRegistry meterRegistry) {
        this(orders, carts, products, null, meterRegistry);
    }

    /**
     * 4.7 引入:5 参构造(带 RefundRepository)— 业务创建路径用。
     * 旧 4 参构造保留用于历史测试/其他不涉及退款的场景,内部把 refunds 显式置 null;
     * 调用退款方法时若 refunds == null,Service 抛 IllegalStateException(防止线上
     * 漏配导致"silent skip 退款")。
     */
    @Autowired
    public OrderService(OrderRepository orders,
                        CartRepository carts,
                        ProductRepository products,
                        RefundRepository refunds,
                        MeterRegistry meterRegistry) {
        this.orders = orders;
        this.carts = carts;
        this.products = products;
        this.refunds = refunds;
        this.meterRegistry = meterRegistry;
    }

    private RefundRepository refunds() {
        if (refunds == null) {
            throw new IllegalStateException(
                    "RefundRepository 未注入 — Spring 容器应注入 5 参构造;"
                    + "历史 4 参构造只用于非退款测试场景");
        }
        return refunds;
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

        List<LineItem> lines = cart.items().stream()
                .filter(CartItem::selected)
                .map(ci -> new LineItem(ci.productId(), ci.quantity()))
                .toList();
        // 1) + 2):逐行校验存在/上架/库存 + 扣减(design.md Gap 2 / D3 共享 helper,
        // 与 create(userId, items) 的 explicit-items 路径共用同一份实现)
        List<OrderItem> items = validateAndDecrementLines(lines);

        // 3) 持久化订单 + 4) 埋点
        OrderResponse response = persistOrderAndRecordMetric(userId, items, paymentMethod);

        // 5) 清空 cart —— 仅 cart 路径才碰 carts repository;
        // explicit-items 直接购买路径(design D3)绝不读/清购物车
        carts.deleteById(userId);

        return response;
    }

    /**
     * mp-backend-contract-gaps Task 2a(design.md Gap 2 / D3):显式 items 直接购买建单,
     * 绕开购物车。{@code items} 为 {@code null}/空 → 回退到现有购物车路径(未变);
     * 非空 → 复用与购物车路径完全相同的逐行校验/扣减 helper 建单,全程不读也不清购物车。
     *
     * @param items 直接购买的行(productId + quantity);null/empty 回退 create(userId)
     */
    public OrderResponse create(String userId, List<CartItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return create(userId);
        }
        List<LineItem> lines = items.stream()
                .map(req -> new LineItem(req.productId(), req.quantity()))
                .toList();
        List<OrderItem> orderItems = validateAndDecrementLines(lines);
        return persistOrderAndRecordMetric(userId, orderItems, "wechat");
    }

    /**
     * mp-backend-contract-gaps Task 2a(design.md Gap 2 / D3):cart 路径与 explicit-items
     * 直接购买路径共用的"逐行校验商品存在/上架/库存 + 扣减"实现,避免两份"商品不存在/
     * 已下架/库存不足"校验逻辑拷贝。
     *
     * @param lines (productId, quantity) 待建单行;cart 路径已提前过滤 selected=false,
     *              explicit-items 路径直接来自请求体
     * @return 校验通过且库存已扣减的 OrderItem 列表,顺序与入参一致
     */
    private List<OrderItem> validateAndDecrementLines(List<LineItem> lines) {
        // 1) 拉所有商品 → 快照 + 校验存在/上架
        List<String> productIds = lines.stream().map(LineItem::productId).toList();
        List<com.seafood.product.infra.ProductDocument> docs = products.findAllById(productIds);
        if (docs.size() != new java.util.HashSet<>(productIds).size()) {
            throw new DomainException("商品不存在或已下架");
        }
        java.util.Map<String, com.seafood.product.infra.ProductDocument> byId = new java.util.HashMap<>();
        for (var d : docs) byId.put(d.getId(), d);

        List<OrderItem> items = new ArrayList<>();
        for (LineItem line : lines) {
            var pd = byId.get(line.productId());
            if (pd == null) {
                throw new DomainException("商品不存在:" + line.productId());
            }
            if (pd.getStatus() != com.seafood.product.domain.ProductStatus.ACTIVE) {
                throw new DomainException("商品已下架:" + pd.getName());
            }
            if (pd.getStock() < line.quantity()) {
                throw new DomainException("库存不足:" + pd.getName() + " (剩余 " + pd.getStock() + ")");
            }
            items.add(new OrderItem(pd.getId(), pd.getName(), pd.getPrice(), line.quantity()));
        }

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
        return items;
    }

    /** cart 路径 / explicit-items 路径共用:持久化订单 + orders.created 埋点。 */
    private OrderResponse persistOrderAndRecordMetric(String userId, List<OrderItem> items, String paymentMethod) {
        BigDecimal total = items.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        Instant now = Instant.now();
        // mp-09 路线图 4.20:预计送达时间 = now + 24h。海鲜商品配送时效约定(本仓库仅单卖家内部运营,
        // 无外部承运商,delivery SLA 由 admin 配置后此处读配置,先写死 24h)。
        Instant estimatedDelivery = now.plus(Duration.ofHours(24));
        Order order = new Order(null, userId, items, total, new OrderStatus.Pending(),
                null, null, null, estimatedDelivery, now, now);
        OrderDocument saved = orders.save(OrderMapper.toDocument(order));

        // 业务埋点(PR #3 3.3):下单成功(库存已扣 + 订单已落库)后累加。失败路径
        // (库存不足/商品下架/购物车空)在 validateAndDecrementLines 内早 throw 处退出,
        // 不递增。paymentMethod tag 无值默认 "wechat"(本期单渠道);
        // Sprint 3 多支付接入后这里扩展为枚举校验。
        String method = (paymentMethod == null || paymentMethod.isBlank()) ? "wechat" : paymentMethod;
        meterRegistry.counter("orders.created", "paymentMethod", method).increment();

        return OrderResponse.from(OrderMapper.toDomain(saved));
    }

    /** cart 路径 / explicit-items 路径共用的建单行(productId + quantity)。 */
    private record LineItem(String productId, int quantity) {}

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

    /**
     * 路线图 4.11 — 按状态分页列退款单(ad-06 列表)。BFF controller 不再
     * 直接访问 RefundRepository(违反 BFF 边界 + controllers 禁 *Repository
     * 依赖),由本服务层封装。
     *
     * <p>status 缺省 → 全量;UI 通常传 REQUESTED(待审)/ APPROVED / REJECTED。
     * 默认按 updatedAt 倒序(最新操作在前)。
     */
    public Page<RefundResponse> listRefunds(String status, Pageable pageable) {
        Page<RefundDocument> page = (status == null || status.isBlank())
                ? refunds.findAll(pageable)
                : refunds.findByStatus(status, pageable);
        return page.map(d -> RefundResponse.from(RefundMapper.toDomain(d)));
    }

    // ----- ship / cancel(状态机)-----

    public OrderResponse ship(String orderId) {
        Order o = load(orderId);
        Order next = o.markShipped(Instant.now());
        return OrderResponse.from(persistAndReturn(next));
    }

    /**
     * 4.13 批量发货(参见 design.md §5.1 + specs/admin-batch-operations §Bulk ship)。
     *
     * <p>策略:逐单处理 + 失败跳过。任一单失败不阻塞其它单(类似 Stripe / Shopify
     * batch API),比"全有或全无"更友好 — admin 不必因 1 单异常重发整批。
     *
     * <p>每单校验:
     * <ul>
     *   <li>订单存在(否则 404 reason)</li>
     *   <li>订单状态为 PAID(否则 DomainException,reason 明确说明当前状态)</li>
     * </ul>
     * 校验失败:收集到 {@code failed} 列表,继续处理下一单;
     * 校验成功:调 {@code markShipped(when)},如有 carrier/trackingNumber 一并挂上
     * (见 4.1 OrderTracking 值对象 — 4.13 与 4.18 共用入口),持久化后 add 到 success。
     *
     * <p>调用方(AdminOrderController)负责鉴权 — ADMIN only。
     *
     * @return 包含 successIds / failed(每项 orderId + reason)/ 统计计数的响应
     */
    public com.seafood.bff.admin.dto.BatchShipResponse batchShip(
            java.util.List<String> orderIds,
            String carrier,
            String trackingNumber) {
        java.util.List<String> success = new java.util.ArrayList<>();
        java.util.List<com.seafood.bff.admin.dto.BatchShipResponse.FailedItem> failed = new java.util.ArrayList<>();

        // 解析一次 tracking(可能为 null — admin 可后续单独录物流)
        OrderTracking tracking = null;
        if (carrier != null && !carrier.isBlank()
                && trackingNumber != null && !trackingNumber.isBlank()) {
            // 至少一个事件,标记"已发货"起始点
            tracking = new OrderTracking(
                    carrier.trim(),
                    trackingNumber.trim(),
                    java.util.List.of(new com.seafood.order.domain.TrackingEvent(
                            Instant.now(), "SHIPPED", "", "已发货")));
        } else if ((carrier != null && !carrier.isBlank())
                || (trackingNumber != null && !trackingNumber.isBlank())) {
            // 部分填写:拒,避免只填承运商没单号这种半截状态
            failed.add(new com.seafood.bff.admin.dto.BatchShipResponse.FailedItem(
                    "(global)", "carrier 与 trackingNumber 必须同时填写或同时留空"));
            // 注意:这种情况不 return,继续处理 orderIds(它们可能没问题)
        }

        for (String orderId : orderIds) {
            try {
                Order o = load(orderId);
                Order shipped = o.markShipped(Instant.now());
                if (tracking != null) {
                    shipped = shipped.attachTracking(tracking);
                }
                persistAndReturn(shipped);
                success.add(orderId);
            } catch (com.seafood.shared.error.NotFoundException e) {
                failed.add(new com.seafood.bff.admin.dto.BatchShipResponse.FailedItem(
                        orderId, "订单不存在"));
            } catch (com.seafood.shared.error.DomainException e) {
                failed.add(new com.seafood.bff.admin.dto.BatchShipResponse.FailedItem(
                        orderId, e.getMessage()));
            }
        }
        return com.seafood.bff.admin.dto.BatchShipResponse.of(success, failed);
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

    // ----- 1.4 / 1.5 sprint-1-closure: 3 新增 customer-side 状态机操作 -----

    /**
     * sprint-1-closure 1.4 — 客户确认收货(SHIPPED → COMPLETED)。
     * 状态机检查见 {@link OrderAction#CONFIRM_RECEIVE};埋点 {@code orders.completed}。
     */
    public OrderResponse confirmReceive(String orderId) {
        Order o = load(orderId);
        if (!OrderAction.CONFIRM_RECEIVE.isAllowedFrom(o.status())) {
            throw new DomainException("Only SHIPPED orders can be confirmed received: current "
                    + o.status().code());
        }
        Order next = o.markCompleted(Instant.now());
        OrderResponse resp = OrderResponse.from(persistAndReturn(next));
        meterRegistry.counter("orders.completed").increment();
        return resp;
    }

    /**
     * sprint-1-closure 1.4 — 客户再次购买(从终态订单重建 cart items)。无状态变更,
     * 仅返回一组 {@code CartItem} 给前端用,前端自己 addToCart。埋点 {@code orders.rebuy}。
     */
    public List<CartItemResponse> rebuy(String orderId) {
        Order o = load(orderId);
        if (!OrderAction.REBUY.isAllowedFrom(o.status())) {
            throw new DomainException("Cannot rebuy order in status " + o.status().code());
        }
        meterRegistry.counter("orders.rebuy").increment();
        return o.items().stream()
                .map(it -> new CartItemResponse(
                        it.productId(), it.quantity(), true, Instant.now()))
                .toList();
    }

    /**
     * sprint-1-closure 1.5 — 提醒发货(PAID 状态发通知,无状态变更)。本期 stub:
     * 调一个 todo-stub,后续接 push 平台。埋点 {@code orders.remind_ship}。
     */
    public void remindShip(String orderId) {
        Order o = load(orderId);
        if (!OrderAction.REMIND_SHIP.isAllowedFrom(o.status())) {
            throw new DomainException("Only PAID orders can be reminded to ship: current "
                    + o.status().code());
        }
        // TODO(sprint-3): call WeChat subscribe-message API
        meterRegistry.counter("orders.remind_ship").increment();
    }

    /**
     * sprint-1-closure 1.3 — 统一的 customer-side 状态机入口。检查 ownership /
     * 当前状态合法性 → 路由到对应业务方法 → 埋点。Controller 后续可以全部改用本方法
     * 取代 4 个分散调用;现存 cancel/markPaid/getTracking 仍保留向后兼容。
     */
    public OrderResponse transition(String orderId, OrderAction action) {
        // 1) 鉴权 + 加载(防 enumeration:非主且非 admin 返 404)
        Order o = load(orderId);
        boolean isAdmin = currentRole() == Role.ADMIN;
        if (!isAdmin && !o.userId().equals(currentUserId())) {
            throw new NotFoundException("订单不存在:" + orderId);
        }
        // 2) 状态机检查
        if (!action.isAllowedFrom(o.status())) {
            throw new DomainException("Action " + action + " not allowed from status "
                    + o.status().code());
        }
        // 3) 路由
        return switch (action) {
            case CANCEL -> cancel(orderId, "user");
            case PAY -> markPaid(orderId);
            case CONFIRM_RECEIVE -> confirmReceive(orderId);
            case REBUY -> {
                rebuy(orderId);
                // REBUY 不改 state,返当前 order
                yield OrderResponse.from(load(orderId));
            }
            case REFUND -> {
                // 复用 requestRefund(amount, reason) 路径,reason 用 action 标签
                RefundResponse rr = requestRefund(orderId, o.totalAmount(), "customer:" + action);
                yield OrderResponse.from(load(orderId));
            }
            case REMIND_SHIP -> {
                remindShip(orderId);
                yield OrderResponse.from(load(orderId));
            }
        };
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

    /**
     * 路线图 4.2:返回订单物流(GET /api/orders/{id}/tracking)。{@code Order.tracking}
     * 在 SHIPPED 之后才挂值,所以 PENDING / PAID / CANCELLED 订单会返回 null(JSON 200,null
     * 字段),不是 404;只有订单本身不存在才 404。
     *
     * <p>鉴权:CUSTOMER 只能查自己单(否则 404 不暴露存在性);ADMIN 查任意。
     * 鉴权失败抛 NotFoundException(与 order not-found 同样语义,防 enumeration)。
     */
    public OrderTracking getTracking(String orderId) {
        Order o = load(orderId);
        boolean isAdmin = currentRole() == Role.ADMIN;
        if (!isAdmin && !o.userId().equals(currentUserId())) {
            // 鉴权失败:对外表现为订单不存在
            throw new NotFoundException("订单不存在:" + orderId);
        }
        return o.tracking();
    }

    public long countCreatedSince(Instant from) {
        return orders.countByCreatedAtGreaterThanEqual(from);
    }

    public BigDecimal sumTotalAmountCreatedSince(Instant from) {
        return orders.findTop500ByOrderByCreatedAtDesc().stream()
                .takeWhile(doc -> doc.getCreatedAt() != null && !doc.getCreatedAt().isBefore(from))
                .map(OrderDocument::getTotalAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ----- 4.7 / 4.8 退款(Refund 生命周期)-----

    /**
     * 4.7:mp 端申请退款。校验:
     * <ul>
     *   <li>订单存在(否则 NotFoundException)</li>
     *   <li>当前用户是订单主或 ADMIN(否则 NotFoundException 防 enumeration — 跟 tracking 同策略)</li>
     *   <li>订单状态允许申请退款(PAID / SHIPPED / COMPLETED 之一;PENDING 未付款,REFUNDING
     *       已有进行中的退款单,REFUNDED / CANCELLED 终态不可申请)</li>
     *   <li>该订单没有未决(REQUESTED)退款单(否则 DomainException — 同单不允许重复申请)</li>
     *   <li>{@code amount} 严格 > 0 且 ≤ {@code Order.totalAmount}(防止超额退款;Refund 构造器
     *       内部还会再校验 > 0 一次)</li>
     *   <li>{@code reason} 非空且 ≤ 200 字符(Refund 构造器内部校验)</li>
     * </ul>
     * 成功路径:创建 Refund(REQUESTED) → Order 转 REFUNDING → 持久化两者。
     * 失败路径:任何校验失败抛 DomainException / NotFoundException,事务未启用不产生副作用。
     */
    public RefundResponse requestRefund(String orderId, BigDecimal amount, String reason) {
        Order o = load(orderId);

        // 鉴权:非订单主且非 ADMIN → 对外表现为订单不存在
        boolean isAdmin = currentRole() == Role.ADMIN;
        if (!isAdmin && !o.userId().equals(currentUserId())) {
            throw new NotFoundException("订单不存在:" + orderId);
        }

        // 状态校验:PENDING / REFUNDING / REFUNDED / CANCELLED 不允许申请
        if (!(o.status() instanceof OrderStatus.Paid
                || o.status() instanceof OrderStatus.Shipped
                || o.status() instanceof OrderStatus.Completed)) {
            throw new DomainException("当前订单状态不允许申请退款:" + o.status().code());
        }

        // 金额校验
        if (amount == null || amount.signum() <= 0) {
            throw new DomainException("退款金额必须大于 0");
        }
        if (amount.compareTo(o.totalAmount()) > 0) {
            throw new DomainException("退款金额不能超过订单总额:" + o.totalAmount());
        }

        // 同单未决退款单:返回 409 语义(DomainException → GlobalExceptionHandler 映射 409)
        refunds().findByOrderId(orderId).ifPresent(existing -> {
            if (!existing.getStatus().equals("APPROVED")
                    && !existing.getStatus().equals("REJECTED")) {
                throw new DomainException("该订单已有进行中的退款申请,状态:" + existing.getStatus());
            }
            // 已 APPROVED / REJECTED 的历史退款单允许再次申请(客服场景:同单分批退)
        });

        Instant now = Instant.now();
        Refund refund = new Refund(
                null, orderId, o.userId(), amount, reason,
                new com.seafood.order.domain.RefundStatus.Requested(),
                now, now);
        RefundDocument savedRefund = refunds().save(RefundMapper.toDocument(refund));

        // 同步:Order 转 REFUNDING + 挂上 refundId(4.20 admin-ui / 详情页依赖)
        Order next = o.markRefunding(now).attachRefundId(savedRefund.getId());
        orders.save(OrderMapper.toDocument(next));

        return RefundResponse.from(RefundMapper.toDomain(savedRefund));
    }

    /**
     * 4.8:admin 同意退款。校验:Refund 存在(否则 404) + 当前状态 REQUESTED(否则 409)。
     * 成功路径:Refund REQUESTED → APPROVED + Order REFUNDING → REFUNDED,持久化两者。
     */
    public RefundResponse approveRefund(String refundId) {
        Refund r = loadRefund(refundId);

        // 优先校验 Refund 状态(早返回,不依赖 Order 加载)—
        // 已批准/已拒绝的退款单直接 409,不抛 NotFoundException(order 可能在别处未就绪)。
        if (!(r.status() instanceof RefundStatus.Requested)) {
            throw new DomainException("退款单状态非 REQUESTED,无法重复同意:当前 " + r.status().code());
        }

        Order o = load(r.orderId());

        // 防御:Order 状态与 Refund 状态应一致(REFUNDING)
        if (!(o.status() instanceof OrderStatus.Refunding)) {
            throw new DomainException("订单状态非 REFUNDING,无法同意退款:" + o.status().code());
        }

        Instant now = Instant.now();
        Refund approved = r.approve(now);
        RefundDocument savedRefund = refunds().save(RefundMapper.toDocument(approved));

        Order refunded = o.markRefunded(now);
        orders.save(OrderMapper.toDocument(refunded));

        // 业务埋点(4.9):admin 同意退款时累加。paymentMethod 暂固定 "wechat"
        // (本期单渠道,Refund 实体未带 paymentMethod 字段 — Sprint 3 接入真实
        // 退款渠道再扩展);amountBucket 复用 OrderMetrics.bucketize(订单金额,4 档
        // 几何分桶 — 严格低基数标签白名单,设计同 orders.paid)。
        String amountBucket = OrderMetrics.bucketize(o.totalAmount());
        meterRegistry.counter("orders.refunded",
                        "paymentMethod", "wechat",
                        "amountBucket", amountBucket)
                .increment();

        return RefundResponse.from(RefundMapper.toDomain(savedRefund));
    }

    /**
     * 4.8:admin 拒绝退款。校验:Refund 存在 + REQUESTED;Order 必须 REFUNDING。
     * 成功路径:Refund REQUESTED → REJECTED + Order REFUNDING → COMPLETED(回退到
     * "已签收"业务态,详见 {@link Order#markRefundRejected} javadoc)。
     *
     * <p>{@code reason} 是 admin 拒绝原因(可选,但 UI 强烈建议填,审计需要)。
     * 当前实现:reason 仅入参校验非空长度,未持久化到 Refund 字段(Sprint 3 4.8 范围内
     * 不引入新字段;后续可加 {@code rejectReason} 列)。
     */
    public RefundResponse rejectRefund(String refundId, String reason) {
        if (reason != null && reason.length() > 200) {
            throw new DomainException("拒绝原因超过 200 字符上限");
        }
        Refund r = loadRefund(refundId);
        Order o = load(r.orderId());

        if (!(o.status() instanceof OrderStatus.Refunding)) {
            throw new DomainException("订单状态非 REFUNDING,无法拒绝退款:" + o.status().code());
        }

        Instant now = Instant.now();
        Refund rejected = r.reject(now);
        RefundDocument savedRefund = refunds().save(RefundMapper.toDocument(rejected));

        Order rolledBack = o.markRefundRejected(now);
        orders.save(OrderMapper.toDocument(rolledBack));

        return RefundResponse.from(RefundMapper.toDomain(savedRefund));
    }

    private Refund loadRefund(String refundId) {
        return refunds().findById(refundId)
                .map(RefundMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("退款单不存在:" + refundId));
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

    /**
     * 4.15:导出最近 N 单为 RFC 4180 CSV(ad-05「导出」按钮,Controller 已加 UTF-8 BOM)。
     *
     * <p>列:订单号 / 用户 ID / 金额(元) / 状态 / 取消原因 / 创建时间 / 更新时间。
     * 物流不在 CSV 内(嵌套结构 Excel 不友好)— ad-06 详情页看。
     *
     * <p>复用 {@link #findRecent(int)} 同一查询路径(findTop500ByOrderByCreatedAtDesc),
     * 与 findRecent 的"500 上限"生产 TODO 同步 — 真要做全量导出应换 Mongo aggregation
     * pipeline + 游标分批。
     *
     * <p>CSV 转义规则(RFC 4180):字段包含 {@code ,} / {@code "} / 换行时,整个字段
     * 用双引号包裹,字段内双引号用 {@code ""} 转义。本方法走集中 helper {@link #csvEscape}
     * 避免散落判断。
     */
    public String exportRecentOrdersAsCsv(int limit) {
        var docs = orders.findTop500ByOrderByCreatedAtDesc();
        int n = Math.min(limit, docs.size());

        StringBuilder sb = new StringBuilder(256 + n * 80);
        sb.append("订单号,用户ID,金额(元),状态,取消原因,创建时间,更新时间\n");
        for (int i = 0; i < n; i++) {
            var d = docs.get(i);
            sb.append(csvEscape(d.getId())).append(',')
              .append(csvEscape(d.getUserId())).append(',')
              .append(d.getTotalAmount() == null ? "" : d.getTotalAmount().toPlainString()).append(',')
              .append(csvEscape(d.getStatus())).append(',')
              .append(csvEscape(d.getCancelReason())).append(',')
              .append(d.getCreatedAt() == null ? "" : d.getCreatedAt().toString()).append(',')
              .append(d.getUpdatedAt() == null ? "" : d.getUpdatedAt().toString()).append('\n');
        }
        return sb.toString();
    }

    /** RFC 4180 CSV 字段转义:含 {@code ,} / {@code "} / 换行 → 用双引号包裹,内部 {@code "} → {@code ""}。 */
    private static String csvEscape(String value) {
        if (value == null) return "";
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuote) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * 4.14:渲染拣货单为可打印 HTML(浏览器 Ctrl+P 另存 PDF)。本期 HTML 兜底,
     * 见 {@code AdminOrderController.printPicklist} javadoc 决策说明。
     * 包含字段:订单号 / 用户 ID / 收货信息(暂无,商品配送地址由后续地址模块接入)
     * / 商品行(name × qty,小计)/ 订单总金额 / 状态 / 创建时间。
     */
    public String renderPicklistHtml(String orderId) {
        Order o = load(orderId);
        StringBuilder sb = new StringBuilder(512 + o.items().size() * 80);
        sb.append("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">")
          .append("<title>拣货单 - ").append(htmlEscape(o.id())).append("</title>")
          .append("<style>")
          .append("body{font-family:system-ui,sans-serif;padding:24px;color:#1a1a1a;}")
          .append("h1{font-size:24px;margin:0 0 16px;}")
          .append("table{width:100%;border-collapse:collapse;margin:16px 0;}")
          .append("th,td{border:1px solid #ccc;padding:8px 12px;text-align:left;}")
          .append("th{background:#f4f4f4;}")
          .append(".right{text-align:right;}")
          .append(".meta{color:#666;font-size:14px;margin-bottom:8px;}")
          .append("@media print{body{padding:0;}.no-print{display:none;}}")
          .append("</style></head><body>")
          .append("<h1>拣货单</h1>")
          .append("<div class=\"meta\">订单号:").append(htmlEscape(o.id())).append("</div>")
          .append("<div class=\"meta\">用户 ID:").append(htmlEscape(o.userId())).append("</div>")
          .append("<div class=\"meta\">状态:").append(o.status().code()).append("</div>")
          .append("<div class=\"meta\">创建时间:").append(o.createdAt()).append("</div>")
          .append("<table><thead><tr><th>#</th><th>商品</th><th class=\"right\">单价</th>")
          .append("<th class=\"right\">数量</th><th class=\"right\">小计</th></tr></thead><tbody>");
        int i = 1;
        for (var it : o.items()) {
            sb.append("<tr>")
              .append("<td>").append(i++).append("</td>")
              .append("<td>").append(htmlEscape(it.productName())).append("</td>")
              .append("<td class=\"right\">¥ ").append(it.unitPrice().toPlainString()).append("</td>")
              .append("<td class=\"right\">").append(it.quantity()).append("</td>")
              .append("<td class=\"right\">¥ ").append(it.subtotal().toPlainString()).append("</td>")
              .append("</tr>");
        }
        sb.append("</tbody><tfoot><tr><th colspan=\"4\" class=\"right\">合计</th>")
          .append("<th class=\"right\">¥ ").append(o.totalAmount().toPlainString()).append("</th></tr></tfoot>")
          .append("</table>")
          .append("<div class=\"no-print\"><button onclick=\"window.print()\">打印 / 另存为 PDF</button></div>")
          .append("</body></html>");
        return sb.toString();
    }

    /** HTML 字段转义:含 {@code <} / {@code >} / {@code &} / {@code "} / {@code '} 时转义。 */
    private static String htmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
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
