package com.seafood.banner.application;

import com.seafood.banner.api.dto.BannerRequest;
import com.seafood.banner.api.dto.BannerResponse;
import com.seafood.banner.domain.Banner;
import com.seafood.banner.domain.BannerStatus;
import com.seafood.banner.infra.BannerDocument;
import com.seafood.banner.infra.BannerMapper;
import com.seafood.banner.infra.BannerRepository;
import com.seafood.product.application.ProductService;
import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Banner 写服务 + 公共读(参见 specs/banner-management)。
 *
 * <p>约束(对齐 product):
 * <ul>
 *   <li>写操作由 Controller {@code @PreAuthorize ADMIN} 守门</li>
 *   <li>公共读 {@link #listActive} 只返回 ACTIVE,按 sortOrder 升序</li>
 *   <li>{@code targetProductId} 非空时经 {@link ProductService} 跨模块校验存在性
 *       (ApplicationService→ApplicationService,不碰 ProductRepository),避免悬空跳转</li>
 * </ul>
 */
@Service
public class BannerService {

    private final BannerRepository repo;
    private final ProductService productService;

    public BannerService(BannerRepository repo, ProductService productService) {
        this.repo = repo;
        this.productService = productService;
    }

    // ----- 公共读 -----

    public List<BannerResponse> listActive() {
        return repo.findByStatusOrderBySortOrderAsc(BannerStatus.ACTIVE).stream()
                .map(BannerMapper::toDomain)
                .map(BannerResponse::from)
                .toList();
    }

    public BannerResponse get(String id) {
        return repo.findById(id)
                .map(BannerMapper::toDomain)
                .map(BannerResponse::from)
                .orElseThrow(() -> new NotFoundException("banner 不存在:" + id));
    }

    // ----- ADMIN -----

    public List<BannerResponse> listAll() {
        return repo.findAllByOrderBySortOrderAsc().stream()
                .map(BannerMapper::toDomain)
                .map(BannerResponse::from)
                .toList();
    }

    public BannerResponse create(BannerRequest req) {
        validateTargetProduct(req.targetProductId());
        Instant now = Instant.now();
        Banner b = new Banner(null, req.tone(), req.emoji(), req.title(), req.subtitle(),
                req.targetProductId(), req.sortOrder(),
                req.active() ? BannerStatus.ACTIVE : BannerStatus.INACTIVE, now, now);
        BannerDocument saved = repo.save(BannerMapper.toDocument(b));
        return BannerResponse.from(BannerMapper.toDomain(saved));
    }

    public BannerResponse update(String id, BannerRequest req) {
        BannerDocument d = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("banner 不存在:" + id));
        validateTargetProduct(req.targetProductId());
        Banner updated = BannerMapper.toDomain(d)
                .updateContent(req.tone(), req.emoji(), req.title(), req.subtitle(),
                        req.targetProductId(), req.sortOrder());
        updated = req.active() ? updated.activate() : updated.deactivate();
        BannerDocument saved = repo.save(BannerMapper.toDocument(updated));
        return BannerResponse.from(BannerMapper.toDomain(saved));
    }

    public void delete(String id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("banner 不存在:" + id);
        }
        repo.deleteById(id);
    }

    /** targetProductId 非空时经 ProductService 校验商品存在;不存在 → DomainException。 */
    private void validateTargetProduct(String targetProductId) {
        if (targetProductId == null || targetProductId.isBlank()) {
            return;
        }
        try {
            productService.get(targetProductId);
        } catch (NotFoundException e) {
            throw new DomainException("targetProductId 指向的商品不存在:" + targetProductId);
        }
    }
}
