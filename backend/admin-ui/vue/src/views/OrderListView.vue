<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useOrderStore } from '@/stores/order'
import { ElMessage } from 'element-plus'
import OrderDetailDialog from '@/components/OrderDetailDialog.vue'
import ShipOrderDialog from '@/components/ShipOrderDialog.vue'
import type { Order } from '@/types'

const orderStore = useOrderStore()

const searchQuery = ref('')
const statusFilter = ref('')
const selectedOrder = ref<Order | null>(null)
const showDetailDialog = ref(false)
const showShipDialog = ref(false)

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待支付', value: 'PENDING' },
  { label: '已支付', value: 'PAID' },
  { label: '已发货', value: 'SHIPPED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' }
]

const filteredOrders = computed(() => {
  let result = orderStore.orders

  if (statusFilter.value) {
    result = result.filter(o => o.displayStatus === statusFilter.value)
  }

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(o =>
      o.id?.toLowerCase().includes(query) ||
      o.orderNumber?.toLowerCase().includes(query) ||
      o.userId?.toLowerCase().includes(query)
    )
  }

  return result
})

function formatPrice(price: number): string {
  return '¥' + (price || 0).toFixed(2)
}

function formatAddress(order: Order): string {
  if (!order.shippingAddress) return '-'
  const addr = order.shippingAddress
  return `${addr.receiverName || ''} ${addr.city || ''}${addr.district || ''} ${addr.address || ''}`
}

function getStatusType(status: string): string {
  const map: Record<string, string> = {
    PENDING: 'warning',
    PAID: 'success',
    SHIPPED: 'primary',
    COMPLETED: 'info',
    CANCELLED: 'danger',
    REFUNDED: 'danger'
  }
  return map[status] || 'info'
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

function handleViewDetail(order: Order) {
  selectedOrder.value = order
  showDetailDialog.value = true
}

function handleShip(order: Order) {
  selectedOrder.value = order
  showShipDialog.value = true
}

async function handleShipConfirm(data: { carrierCode: string; carrierName: string; trackingNumber: string }) {
  if (!selectedOrder.value) return

  const success = await orderStore.shipOrder(selectedOrder.value.id, data)
  if (success) {
    ElMessage.success(`订单 ${selectedOrder.value.orderNumber} 已发货`)
    showShipDialog.value = false
  } else {
    ElMessage.error(orderStore.error || '发货失败')
  }
}

onMounted(() => {
  orderStore.fetchOrders()
})
</script>

<template>
  <div class="order-list">
    <!-- Page header -->
    <div class="page-header">
      <div>
        <h1 class="page-header__title">订单管理</h1>
        <p class="page-header__subtitle">管理所有订单，处理发货请求</p>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="toolbar">
      <el-select
        v-model="statusFilter"
        placeholder="订单状态"
        clearable
        style="width: 140px;"
      >
        <el-option
          v-for="option in statusOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>

      <el-input
        v-model="searchQuery"
        placeholder="搜索订单号/用户ID..."
        clearable
        style="max-width: 300px;"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-button @click="orderStore.fetchOrders">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <!-- Loading state -->
    <el-skeleton :loading="orderStore.loading" animated>
      <!-- Orders table -->
      <el-table
        :data="filteredOrders"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="id" label="订单ID" width="180" show-overflow-tooltip />
        <el-table-column prop="orderNumber" label="订单号" width="140" show-overflow-tooltip />
        <el-table-column prop="userId" label="用户ID" width="140" show-overflow-tooltip />
        <el-table-column label="总价" width="100">
          <template #default="{ row }">
            <span class="price">{{ formatPrice(row.totalPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.displayStatus)" size="small">
              {{ getStatusLabel(row.displayStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="收货地址" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatAddress(row) }}
          </template>
        </el-table-column>
        <el-table-column label="商品数量" width="90">
          <template #default="{ row }">
            {{ row.items?.length || 0 }} 件
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewDetail(row)">详情</el-button>
            <el-button
              v-if="row.displayStatus === 'PAID'"
              link
              type="danger"
              @click="handleShip(row)"
            >
              发货
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-skeleton>

    <!-- Order detail dialog -->
    <OrderDetailDialog
      v-model:visible="showDetailDialog"
      :order="selectedOrder"
      @close="showDetailDialog = false"
    />

    <!-- Ship order dialog -->
    <ShipOrderDialog
      v-model:visible="showShipDialog"
      :order="selectedOrder"
      @confirm="handleShipConfirm"
      @close="showShipDialog = false"
    />
  </div>
</template>

<script lang="ts">
import { Search, Refresh } from '@element-plus/icons-vue'
export default {
  components: { Search, Refresh }
}
</script>

<style scoped>
.order-list {
  max-width: 1600px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header__title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.page-header__subtitle {
  color: var(--text-muted);
  font-size: 0.9rem;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.price {
  font-weight: 600;
  color: var(--color-danger);
}
</style>
