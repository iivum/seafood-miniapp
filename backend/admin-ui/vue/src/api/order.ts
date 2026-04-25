import api from './axios'
import type { Order, ShipOrderRequest } from '@/types'

export const orderApi = {
  getAll: async (): Promise<Order[]> => {
    const response = await api.get('/orders')
    return response.data
  },

  getById: async (id: string): Promise<Order> => {
    const response = await api.get(`/orders/${id}`)
    return response.data
  },

  updateStatus: async (id: string, status: string): Promise<Order> => {
    const response = await api.put(`/orders/${id}/status?status=${status}`)
    return response.data
  },

  shipOrder: async (id: string, data: ShipOrderRequest): Promise<Order> => {
    const response = await api.put(`/orders/${id}/ship`, data)
    return response.data
  }
}
