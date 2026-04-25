<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '@/api/axios'
import { useFormatters } from '@/composables/useFormatters'
import { useStatusHelper } from '@/composables/useStatusHelper'
import {
  Goods,
  ShoppingCart,
  User as UserIcon,
  Money,
  Warning,
  Check,
  Clock,
  Truck,
  CircleCheck
} from '@element-plus/icons-vue'

const { formatPrice } = useFormatters()
const { statusColors } = useStatusHelper()

interface DashboardData {
  totalProducts: number
  totalOrders: number
  totalUsers: number
  paidOrders: number
  shippedOrders: number
  completedOrders: number
  pendingOrders: number
  totalRevenue: number
  lowStockProducts: number
  statusCounts: Record<string, number>
}

const loading = ref(true)
const data = ref<DashboardData | null>(null)

const totalProducts = computed(() => data.value?.totalProducts ?? 0)
const totalOrders = computed(() => data.value?.totalOrders ?? 0)
const totalUsers = computed(() => data.value?.totalUsers ?? 0)
const paidOrders = computed(() => data.value?.paidOrders ?? 0)
const shippedOrders = computed(() => data.value?.shippedOrders ?? 0)
const completedOrders = computed(() => data.value?.completedOrders ?? 0)
const totalRevenue = computed(() => data.value?.totalRevenue ?? 0)
const lowStockProducts = computed(() => data.value?.lowStockProducts ?? 0)
const statusCounts = computed(() => data.value?.statusCounts ?? {})

async function fetchData() {
  loading.value = true
  try {
    const response = await api.get<DashboardData>('/admin/dashboard')
    data.value = response.data
  } catch (error) {
    console.error('Failed to fetch dashboard data:', error)
  } finally {
    loading.value = false
  }
}

function formatPercent(count: number, total: number): string {
  if (total === 0) return '0%'
  return ((count / total) * 100).toFixed(0) + '%'
}

onMounted(fetchData)
</script>

<template>
  <div class="dashboard">
    <!-- Page header -->
    <div class="page-header">
      <div>
        <h1 class="page-header__title">📊 数据概览</h1>
        <p class="page-header__subtitle">实时统计商城运营数据</p>
      </div>
    </div>

    <el-skeleton :loading="loading" animated>
      <!-- Stats cards -->
      <div class="card-grid">
        <div class="stat-card stat-card--blue">
          <div class="stat-card__icon" style="background: rgba(0, 150, 199, 0.1);">
            <el-icon :size="24" color="#0096c7"><Goods /></el-icon>
          </div>
          <div class="stat-card__title">商品总数</div>
          <div class="stat-card__value">{{ totalProducts }}</div>
          <div class="stat-card__subtitle">
            <template v-if="lowStockProducts > 0">
              <el-icon color="#f57f17"><Warning /></el-icon>
              {{ lowStockProducts }} 件库存预警
            </template>
            <template v-else>库存充足</template>
          </div>
        </div>

        <div class="stat-card stat-card--blue">
          <div class="stat-card__icon" style="background: rgba(2, 62, 106, 0.1);">
            <el-icon :size="24" color="#023e6a"><ShoppingCart /></el-icon>
          </div>
          <div class="stat-card__title">订单总数</div>
          <div class="stat-card__value">{{ totalOrders }}</div>
          <div class="stat-card__subtitle">
            已完成 {{ completedOrders }} | 待发货 {{ paidOrders }}
          </div>
        </div>

        <div class="stat-card stat-card--blue">
          <div class="stat-card__icon" style="background: rgba(0, 119, 182, 0.1);">
            <el-icon :size="24" color="#0077b6"><UserIcon /></el-icon>
          </div>
          <div class="stat-card__title">用户总数</div>
          <div class="stat-card__value">{{ totalUsers }}</div>
          <div class="stat-card__subtitle">管理后台活跃用户</div>
        </div>

        <div class="stat-card stat-card--blue">
          <div class="stat-card__icon" style="background: rgba(2, 62, 106, 0.1);">
            <el-icon :size="24" color="#023e6a"><Money /></el-icon>
          </div>
          <div class="stat-card__title">销售收入</div>
          <div class="stat-card__value">{{ formatPrice(totalRevenue) }}</div>
          <div class="stat-card__subtitle">已发货 {{ shippedOrders }} 笔</div>
        </div>
      </div>

      <!-- Order status section -->
      <div class="dashboard__section">
        <div class="section-header">
          <h2 class="section-header__title">📈 订单状态分布</h2>
          <p class="section-header__subtitle">各状态订单数量与占比</p>
        </div>

        <div class="status-cards">
          <div
            v-for="(count, status) in statusCounts"
            :key="status"
            class="status-card"
            :style="{
              background: `${statusColors[status]}15`,
              borderColor: `${statusColors[status]}30`
            }"
          >
            <div class="status-card__icon" :style="{ color: statusColors[status] }">
              <el-icon v-if="status === 'PENDING'" :size="20"><Clock /></el-icon>
              <el-icon v-else-if="status === 'PAID'" :size="20"><Check /></el-icon>
              <el-icon v-else-if="status === 'SHIPPED'" :size="20"><Truck /></el-icon>
              <el-icon v-else-if="status === 'COMPLETED'" :size="20"><CircleCheck /></el-icon>
              <el-icon v-else :size="20"><Warning /></el-icon>
            </div>
            <div class="status-card__label">
              {{ status === 'PENDING' ? '待支付' :
                 status === 'PAID' ? '已支付' :
                 status === 'SHIPPED' ? '已发货' :
                 status === 'COMPLETED' ? '已完成' : '已取消' }}
            </div>
            <div class="status-card__count" :style="{ color: statusColors[status] }">
              {{ count }}
            </div>
          </div>
        </div>

        <!-- Progress bars -->
        <div v-if="totalOrders > 0" class="progress-section">
          <div v-if="statusCounts.PAID > 0" class="progress-item">
            <div class="progress-item__label">
              <span>已支付</span>
              <span class="progress-item__value">{{ statusCounts.PAID }} ({{ formatPercent(statusCounts.PAID, totalOrders) }})</span>
            </div>
            <el-progress
              :percentage="Number(formatPercent(statusCounts.PAID, totalOrders))"
              :stroke-width="10"
              :color="statusColors.PAID"
              :show-text="false"
            />
          </div>

          <div v-if="statusCounts.SHIPPED > 0" class="progress-item">
            <div class="progress-item__label">
              <span>已发货</span>
              <span class="progress-item__value">{{ statusCounts.SHIPPED }} ({{ formatPercent(statusCounts.SHIPPED, totalOrders) }})</span>
            </div>
            <el-progress
              :percentage="Number(formatPercent(statusCounts.SHIPPED, totalOrders))"
              :stroke-width="10"
              :color="statusColors.SHIPPED"
              :show-text="false"
            />
          </div>

          <div v-if="statusCounts.COMPLETED > 0" class="progress-item">
            <div class="progress-item__label">
              <span>已完成</span>
              <span class="progress-item__value">{{ statusCounts.COMPLETED }} ({{ formatPercent(statusCounts.COMPLETED, totalOrders) }})</span>
            </div>
            <el-progress
              :percentage="Number(formatPercent(statusCounts.COMPLETED, totalOrders))"
              :stroke-width="10"
              :color="statusColors.COMPLETED"
              :show-text="false"
            />
          </div>
        </div>

        <el-empty v-else description="暂无订单数据" />
      </div>
    </el-skeleton>
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 1400px;
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

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
  margin-bottom: 32px;
}

.stat-card {
  background: var(--card-bg);
  border-radius: var(--radius-lg);
  padding: 20px;
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-normal);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stat-card__icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}

.stat-card__title {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 8px;
}

.stat-card__value {
  font-size: 1.85rem;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1;
  margin-bottom: 8px;
}

.stat-card__subtitle {
  font-size: 0.75rem;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 4px;
}

.dashboard__section {
  background: var(--card-bg);
  border-radius: var(--radius-lg);
  padding: 24px;
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-sm);
}

.section-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 2px solid var(--border-color);
}

.section-header__title {
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.section-header__subtitle {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.status-cards {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 24px;
}

.status-card {
  flex: 1;
  min-width: 100px;
  padding: 16px;
  border-radius: var(--radius-md);
  border: 1px solid;
  text-align: center;
  transition: all var(--transition-fast);
}

.status-card:hover {
  transform: scale(1.02);
}

.status-card__icon {
  margin-bottom: 8px;
}

.status-card__label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.status-card__count {
  font-size: 1.5rem;
  font-weight: 800;
  line-height: 1;
}

.progress-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.progress-item__label {
  display: flex;
  justify-content: space-between;
  font-size: 0.85rem;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.progress-item__value {
  font-weight: 600;
  color: var(--text-primary);
}
</style>
