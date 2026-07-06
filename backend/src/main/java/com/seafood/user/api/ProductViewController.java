package com.seafood.user.api;

import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.ProductViewResponse;
import com.seafood.user.application.ProductViewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 浏览足迹 API(self-scoped 门面,同 {@link FavoriteController} 惯例)。
 * {@link #record} 是 best-effort 记录(design.md D6:失败不影响商品详情页渲染),
 * 204 No Content——前端不关心返回体。
 */
@RestController
@RequestMapping("/api/product-views")
public class ProductViewController {

    private final ProductViewService productViews;

    public ProductViewController(ProductViewService productViews) {
        this.productViews = productViews;
    }

    @PostMapping("/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> record(@PathVariable String productId, @AuthenticationPrincipal UserPrincipal me) {
        productViews.record(me.getId(), productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ProductViewResponse> list(@AuthenticationPrincipal UserPrincipal me) {
        return productViews.list(me.getId());
    }
}
