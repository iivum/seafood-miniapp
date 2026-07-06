package com.seafood.user.api;

import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.FavoriteItemResponse;
import com.seafood.user.application.FavoriteService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收藏 API(self-scoped 门面,同 {@code AddressController}/{@code CartController}
 * 既有惯例——身份取自 JWT principal,不接受外部 userId 参数)。
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favorites;

    public FavoriteController(FavoriteService favorites) {
        this.favorites = favorites;
    }

    @PostMapping("/{productId}")
    @PreAuthorize("isAuthenticated()")
    public List<String> add(@PathVariable String productId, @AuthenticationPrincipal UserPrincipal me) {
        return favorites.addFavorite(me.getId(), productId);
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("isAuthenticated()")
    public List<String> remove(@PathVariable String productId, @AuthenticationPrincipal UserPrincipal me) {
        return favorites.removeFavorite(me.getId(), productId);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<FavoriteItemResponse> list(@AuthenticationPrincipal UserPrincipal me) {
        return favorites.list(me.getId());
    }
}
