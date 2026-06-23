package com.seafood.featureflag.infra;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

/**
 * feature_flags collection 的 Spring Data MongoDB Repository。
 */
public interface FeatureFlagRepository extends MongoRepository<FeatureFlagDocument, String> {

    Optional<FeatureFlagDocument> findByFlagKey(String flagKey);

    List<FeatureFlagDocument> findAllByEnabledTrue();

    boolean existsByFlagKey(String flagKey);
}
