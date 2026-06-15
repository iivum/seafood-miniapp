package com.seafood.order.infra;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * refunds collection(参见 design.md §6.1 + specs/admin-batch-operations)。
 *
 * <p>索引策略:
 * <ul>
 *   <li>{@code orderId} 索引 — 按订单反查退款单(4.7「创建退款单 + 改 Order 状态」同步落);</li>
 *   <li>{@code userId} 索引 — mp 端「我的退款」按用户过滤(Sprint 3 4.10 UI 用);</li>
 *   <li>{@code status} 索引 — admin 端「待审核退款」列表(4.11 UI 用)。</li>
 * </ul>
 */
@Document(collection = "refunds")
public class RefundDocument {

    @Id
    private String id;

    @Indexed
    private String orderId;

    @Indexed
    private String userId;

    @Indexed
    private String status;

    private BigDecimal amount;
    private String reason;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
