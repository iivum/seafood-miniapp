<script setup lang="ts">
import type { Order } from '@/types'

defineProps<{
  visible: boolean
  order: Order | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'close'): void
}>()

function formatPrice(price: number): string {
  return '¥' + (price || 0).toFixed(2)
}

function formatDate(dateStr: string | undefined): string {
  if (!dateStr) return '-'
  return dateStr
}

function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '待支付',
    PAID: '已支付',
    SHIPPED: '已发货',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    REFUNDED: '已退款'
  }
  return map[status] || status
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="订单详情"
    width="600px"
    :close-on-click-modal="true"
    @update:model-value="emit('update:visible', $event)"
    @close="emit('close')"
  >
    <div v-if="order" class="order-detail">
      <!-- Order info -->
      <div class="detail-section">
        <h3 class="detail-section__title">基本信息</h3>
        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-item__label">订单ID</span>
            <span class="detail-item__value">{{ order.id }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-item__label">订单号</span>
            <span class="detail-item__value">{{ order.orderNumber }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-item__label">用户ID</span>
            <span class="detail-item__value">{{ order.userId }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-item__label">订单状态</span>
            <span class="detail-item__value">
              <el-tag size="small">{{ getStatusLabel(order.displayStatus) }}</el-tag>
            </span>
          </div>
        </div>
      </div>

      <!-- Shipping address -->
      <div class="detail-section">
        <h3 class="detail-section__title">收货信息</h3>
        <div class="detail-grid">
          <div class="detail-item detail-item--full">
            <span class="detail-item__label">收货地址</span>
            <span class="detail-item__value">
              <template v-if="order.shippingAddress">
                {{ order.shippingAddress.receiverName }}
                {{ order.shippingAddress.city }}{{ order.shippingAddress.district }}
                {{ order.shippingAddress.address }}
                {{ order.shippingAddress.phone }}
              </template>
              <template v-else>-</template>
            </span>
          </div>
        </div>
      </div>

      <!-- Price info -->
      <div class="detail-section">
        <h3 class="detail-section__title">费用信息</h3>
        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-item__label">商品总价</span>
            <span class="detail-item__value">{{ formatPrice(order.totalPrice) }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-item__label">运费</span>
            <span class="detail-item__value">{{ formatPrice(order.shippingFee) }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-item__label">优惠</span>
            <span class="detail-item__value">-{{ formatPrice(order.discountAmount) }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-item__label">实付金额</span>
            <span class="detail-item__value detail-item__value--highlight">
              {{ formatPrice(order.finalPrice) }}
            </span>
          </div>
        </div>
      </div>

      <!-- Items -->
      <div class="detail-section">
        <h3 class="detail-section__title">商品清单</h3>
        <el-table :data="order.items" border size="small">
          <el-table-column prop="productName" label="商品名称" />
          <el-table-column prop="price" label="单价" width="100">
            <template #default="{ row }">
              {{ formatPrice(row.price) }}
            </template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column label="小计" width="100">
            <template #default="{ row }">
              {{ formatPrice(row.price * row.quantity) }}
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Timeline -->
      <div v-if="order.orderHistory?.length" class="detail-section">
        <h3 class="detail-section__title">订单历史</h3>
        <el-timeline>
          <el-timeline-item
            v-for="(item, index) in order.orderHistory"
            :key="index"
            :timestamp="formatDate(item.timestamp)"
          >
            {{ item.note || getStatusLabel(item.status) }}
          </el-timeline-item>
        </el-timeline>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('close')">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.order-detail {
  max-height: 60vh;
  overflow-y: auto;
}

.detail-section {
  margin-bottom: 20px;
}

.detail-section__title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-item--full {
  grid-column: 1 / -1;
}

.detail-item__label {
  font-size: 0.75rem;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.detail-item__value {
  font-size: 0.9rem;
  color: var(--text-primary);
}

.detail-item__value--highlight {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--color-danger);
}
</style>
