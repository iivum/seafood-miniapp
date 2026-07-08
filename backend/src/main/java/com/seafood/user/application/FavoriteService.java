package com.seafood.user.application;

import com.seafood.product.api.dto.ProductResponse;
import com.seafood.product.application.ProductService;
import com.seafood.shared.error.NotFoundException;
import com.seafood.user.api.dto.FavoriteItemResponse;
import com.seafood.user.domain.User;
import com.seafood.user.infra.UserDocument;
import com.seafood.user.infra.UserMapper;
import com.seafood.user.infra.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收藏服务(收藏 + 浏览足迹,design.md)。self-scoped——身份始终是调用者本人
 * (由 {@link FavoriteController} 从 JWT principal 取,不接受外部 userId 参数,
 * 同 {@code AddressController} 既有惯例),不需要额外授权校验。
 *
 * <p>富化商品信息 + 失效商品降级复用 {@code CartService#enrich} 已确立的先例
 * (ApplicationService → ApplicationService,不直碰 ProductRepository)。
 */
@Service
public class FavoriteService {

    private static final String UNAVAILABLE_PRODUCT_NAME = "商品已下架";

    private final UserRepository users;
    private final ProductService productService;

    public FavoriteService(UserRepository users, ProductService productService) {
        this.users = users;
        this.productService = productService;
    }

    public List<String> addFavorite(String userId, String productId) {
        User u = loadOrThrow(userId);
        User updated = u.addFavorite(productId);
        persist(updated);
        return updated.favoriteProductIds();
    }

    public List<String> removeFavorite(String userId, String productId) {
        User u = loadOrThrow(userId);
        User updated = u.removeFavorite(productId);
        persist(updated);
        return updated.favoriteProductIds();
    }

    public List<FavoriteItemResponse> list(String userId) {
        User u = loadOrThrow(userId);
        return u.favoriteProductIds().stream().map(this::enrich).toList();
    }

    private FavoriteItemResponse enrich(String productId) {
        try {
            ProductResponse p = productService.get(productId);
            return new FavoriteItemResponse(productId, p.name(), p.price(), p.imageUrl(), true);
        } catch (NotFoundException e) {
            return new FavoriteItemResponse(productId, UNAVAILABLE_PRODUCT_NAME, BigDecimal.ZERO, "", false);
        }
    }

    private User loadOrThrow(String userId) {
        return users.findById(userId)
                .map(UserMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("用户不存在:" + userId));
    }

    private void persist(User u) {
        users.save(UserMapper.toDocument(u));
    }
}
