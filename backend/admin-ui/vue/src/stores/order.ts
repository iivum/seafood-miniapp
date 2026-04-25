import { defineStore } from 'pinia'
import { ref } from 'vue'
import { orderApi } from '@/api/order'
import type { Order, ShipOrderRequest } from '@/types'

export const useOrderStore = defineStore('order', () => {
  const orders = ref<Order[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchOrders() {
    loading.value = true
    error.value = null
    try {
      orders.value = await orderApi.getAll()
    } catch (e) {
      error.value = e instanceof Error ? e.message : '获取订单列表失败'
    } finally {
      loading.value = false
    }
  }

  async function updateOrderStatus(id: string, status: string): Promise<boolean> {
    try {
      await orderApi.updateStatus(id, status)
      await fetchOrders()
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '更新订单状态失败'
      return false
    }
  }

  async function shipOrder(id: string, data: ShipOrderRequest): Promise<boolean> {
    try {
      await orderApi.shipOrder(id, data)
      await fetchOrders()
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '发货失败'
      return false
    }
  }

  return {
    orders,
    loading,
    error,
    fetchOrders,
    updateOrderStatus,
    shipOrder
  }
})
