import api from './axios'
import type { Product, CreateProductRequest } from '@/types'

export const productApi = {
  getAll: async (): Promise<Product[]> => {
    const response = await api.get('/products')
    return response.data
  },

  getById: async (id: string): Promise<Product> => {
    const response = await api.get(`/products/${id}`)
    return response.data
  },

  create: async (data: CreateProductRequest): Promise<Product> => {
    const response = await api.post('/products', data)
    return response.data
  },

  update: async (id: string, data: CreateProductRequest): Promise<Product> => {
    const response = await api.put(`/products/${id}`, data)
    return response.data
  },

  delete: async (id: string): Promise<void> => {
    await api.delete(`/products/${id}`)
  }
}
