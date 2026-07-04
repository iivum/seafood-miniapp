package com.seafood.order.domain;

/**
 * 购物车中不存在指定 productId 对应的行(design.md Gap 1 / D1)。
 *
 * <p>与"数量非法"等其它域校验异常(见 {@link com.seafood.shared.error.DomainException})区分开,
 * 让 {@code CartService} 能精确捕获"查找失败"这一种情形并译为 404,而不会连带把数量校验失败
 * 也误译成 404 —— 两者语义不同(前者是路径资源不存在,后者是请求体非法)。
 */
public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(String productId) {
        super("购物车中不存在该商品行:" + productId);
    }
}
