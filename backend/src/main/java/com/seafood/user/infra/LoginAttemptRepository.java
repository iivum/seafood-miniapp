package com.seafood.user.infra;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

/**
 * sprint-1-closure 2.2 — {@link LoginAttemptDocument} 仓储。
 *
 * <p>findByIpAndSuccessAndTsAfter / findByAccountAndSuccessAndTsAfter 用于
 * 滚动窗口内失败次数统计(阈值 3 / 15min)。
 */
public interface LoginAttemptRepository extends MongoRepository<LoginAttemptDocument, String> {

    long countByIpAndSuccessAndTsAfter(String ip, boolean success, Instant after);

    long countByAccountAndSuccessAndTsAfter(String account, boolean success, Instant after);

    List<LoginAttemptDocument> findByIpAndTsAfterOrderByTsDesc(String ip, Instant after);

    List<LoginAttemptDocument> findByAccountAndTsAfterOrderByTsDesc(String account, Instant after);
}
