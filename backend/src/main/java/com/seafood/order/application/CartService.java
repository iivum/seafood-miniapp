package com.seafood.order.application;

import com.seafood.order.api.dto.CartItemRequest;
import com.seafood.order.api.dto.CartLineItemResponse;
import com.seafood.order.api.dto.CartResponse;
import com.seafood.order.domain.Cart;
import com.seafood.order.domain.CartItem;
import com.seafood.order.infra.CartDocument;
import com.seafood.order.infra.CartRepository;
import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.application.ProductService;
import com.seafood.shared.error.NotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 购物车服务(参见 specs/backend-api §Customer cart operations)。
 * 所有方法都接收 userId 参数(由 Controller 从 SecurityContext 注入),
 * 不接受 query/path 中的 userId → 防止越权。
 *
 * <p>mp-04 购物车 OD 对齐 brief:响应经 {@link ProductService} 富化商品名/单价/图片
 * (ApplicationService → ApplicationService,不碰 ProductRepository,同
 * {@code BannerService} 校验 targetProductId 的既有先例)。购物车里指向已下架/被删除商品的行
 * 是正常业务场景,单行降级(available=false),不能让一个失效商品 500 掉整个购物车请求。
 */
@Service
public class CartService {

    private static final String UNAVAILABLE_PRODUCT_NAME = "商品已下架";

    private final CartRepository carts;
    private final ProductService productService;

    public CartService(CartRepository carts, ProductService productService) {
        this.carts = carts;
        this.productService = productService;
    }

    public CartResponse get(String userId) {
        return toResponse(loadOrEmpty(userId));
    }

    public CartResponse addItem(String userId, CartItemRequest req) {
        Cart current = loadOrEmpty(userId);
        Cart updated = current.addItem(req.productId(), req.quantity());
        return toResponse(persist(updated));
    }

    public CartResponse removeItem(String userId, String productId) {
        Cart current = loadOrEmpty(userId);
        Cart updated = current.removeItem(productId);
        return toResponse(persist(updated));
    }

    public void clear(String userId) {
        carts.deleteById(userId);
    }

    // ----- helpers -----

    private Cart loadOrEmpty(String userId) {
        return carts.findById(userId)
                .map(d -> new Cart(d.getUserId(), d.getItems(), d.getUpdatedAt()))
                .orElseGet(() -> Cart.empty(userId));
    }

    private Cart persist(Cart c) {
        CartDocument d = new CartDocument();
        d.setUserId(c.userId());
        d.setItems(c.items());
        d.setUpdatedAt(c.updatedAt() == null ? Instant.now() : c.updatedAt());
        carts.save(d);
        return c;
    }

    /** 域对象 Cart → 富化后的 CartResponse:逐行经 ProductService 查商品名/单价/图片。 */
    private CartResponse toResponse(Cart cart) {
        List<CartLineItemResponse> items = cart.items().stream()
                .map(this::enrich)
                .toList();
        return new CartResponse(cart.userId(), items, cart.updatedAt());
    }

    /**
     * 单行富化。商品不存在(已下架/被删除)时降级返回占位数据,不让异常向上冒泡——
     * 购物车里存在指向失效商品的行是正常业务场景,不能因为一行失效就 500 掉整个请求。
     */
    private CartLineItemResponse enrich(CartItem item) {
        try {
            ProductResponse product = productService.get(item.productId());
            return new CartLineItemResponse(
                    item.productId(), product.name(), product.price(), product.imageUrl(),
                    item.quantity(), item.selected(), item.addedAt(), true);
        } catch (NotFoundException e) {
            return new CartLineItemResponse(
                    item.productId(), UNAVAILABLE_PRODUCT_NAME, BigDecimal.ZERO, "",
                    item.quantity(), item.selected(), item.addedAt(), false);
        }
    }
}
