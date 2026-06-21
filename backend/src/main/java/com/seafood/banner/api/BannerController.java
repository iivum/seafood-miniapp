package com.seafood.banner.api;

import com.seafood.banner.api.dto.BannerRequest;
import com.seafood.banner.api.dto.BannerResponse;
import com.seafood.banner.application.BannerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Banner API(参见 specs/banner-management)。
 * 公共读匿名(只返 ACTIVE),写操作 + 全量列表 ADMIN 限定
 * (SecurityConfig URL 级 + @PreAuthorize 双重防护,对齐 ProductController)。
 */
@RestController
@RequestMapping("/api/banners")
public class BannerController {

    private final BannerService banners;

    public BannerController(BannerService banners) {
        this.banners = banners;
    }

    @GetMapping
    public List<BannerResponse> list() {
        return banners.listActive();
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<BannerResponse> listAll() {
        return banners.listAll();
    }

    @GetMapping("/{id}")
    public BannerResponse get(@PathVariable String id) {
        return banners.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BannerResponse> create(@Valid @RequestBody BannerRequest req) {
        BannerResponse created = banners.create(req);
        return ResponseEntity.created(URI.create("/api/banners/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BannerResponse update(@PathVariable String id, @Valid @RequestBody BannerRequest req) {
        return banners.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        banners.delete(id);
        return ResponseEntity.noContent().build();
    }
}
