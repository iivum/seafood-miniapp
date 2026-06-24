package com.seafood.featureflag.infra;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * feature_flag_audits collection 的 Spring Data MongoDB Repository。
 */
public interface FeatureFlagAuditRepository extends MongoRepository<FeatureFlagAuditDocument, String> {

    Page<FeatureFlagAuditDocument> findByFlagKeyOrderByTimestampDesc(String flagKey, Pageable pageable);
}
