package com.seafood.banner.infra;

import com.seafood.banner.domain.BannerStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BannerRepository extends MongoRepository<BannerDocument, String> {

    /** 公共列表:按 status 过滤 + sortOrder 升序。 */
    List<BannerDocument> findByStatusOrderBySortOrderAsc(BannerStatus status);

    /** admin 全量:全部 banner 按 sortOrder 升序(含 INACTIVE)。 */
    List<BannerDocument> findAllByOrderBySortOrderAsc();
}
