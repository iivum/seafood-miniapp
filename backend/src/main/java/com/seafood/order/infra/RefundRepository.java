package com.seafood.order.infra;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * refunds collection 仓储(参见 design.md §6.1 + specs/admin-batch-operations)。
 *
 * <p>当前(Sprint 3 4.6 引入)只暴露基础 CRUD + 状态过滤查询;后续 4.7 引入 createRefund
 * 业务方法时,OrderService 跨模块调 {@code refunds.save(...)}(经 ApplicationService 边界,
 * 符合 design §1.3「跨模块只走 ApplicationService」约束)。
 */
public interface RefundRepository extends MongoRepository<RefundDocument, String> {

    /** 按订单 ID 查找(应只有 1 条;Order 终态前不允许重复申请)。 */
    Optional<RefundDocument> findByOrderId(String orderId);

    /** 按订单 ID 列表批量查(预留 ad-06 订单详情「相关退款」tab)。 */
    List<RefundDocument> findByOrderIdIn(List<String> orderIds);

    /** admin 端按状态分页(REQUESTED 待审核 / APPROVED 已通过 / REJECTED 已拒绝)。 */
    Page<RefundDocument> findByStatus(String status, Pageable pageable);
}
