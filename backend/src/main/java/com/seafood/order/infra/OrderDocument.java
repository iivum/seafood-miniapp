package com.seafood.order.infra;

import com.seafood.order.domain.OrderItem;
import com.seafood.order.domain.OrderTracking;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** orders collection(参见 design.md §6.1 + Sprint 3 4.1 tracking 字段)。 */
@Document(collection = "orders")
public class OrderDocument {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String status;

    private List<OrderItem> items;

    /**
     * fix-order-amount-contract:商品小计(= Σ items.subtotal)、运费、优惠(design.md
     * 决策 1)。null 时(改动前创建的历史订单)MongoDB 不写字段,读取按 0 兜底
     * (决策 3——不回填历史数据,{@code totalAmount} 保持原值不变,零迁移)。
     */
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal discount;

    private BigDecimal totalAmount;
    private String cancelReason;

    /**
     * 物流信息(4.1 新增,SHIPPED / COMPLETED 状态才有值)。null 时 MongoDB 不写字段,
     * 旧订单无此字段反序列化为 null — 零迁移,向后兼容。
     */
    private OrderTracking tracking;

    /**
     * 退款单 id(4.20 新增,REFUNDING/REFUNDED 状态挂值)。null 时 MongoDB 不写字段,
     * 旧订单无此字段反序列化为 null — 零迁移,向后兼容。
     */
    private String refundId;

    /**
     * 预计送达时间(mp-09 4.20 新增,create 时由 Service 算 now+24h 写入)。
     * null 时 MongoDB 不写字段,旧订单无此字段反序列化为 null — 零迁移,向后兼容。
     */
    private Instant estimatedDelivery;

    @Indexed(direction = org.springframework.data.mongodb.core.index.IndexDirection.DESCENDING)
    private Instant createdAt;

    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public OrderTracking getTracking() { return tracking; }
    public void setTracking(OrderTracking tracking) { this.tracking = tracking; }

    public String getRefundId() { return refundId; }
    public void setRefundId(String refundId) { this.refundId = refundId; }

    public Instant getEstimatedDelivery() { return estimatedDelivery; }
    public void setEstimatedDelivery(Instant estimatedDelivery) { this.estimatedDelivery = estimatedDelivery; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
