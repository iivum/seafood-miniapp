import api from './axios'
import type { User } from '@/types'

export const userApi = {
  getAll: async (): Promise<User[]> => {
    const response = await api.get('/users')
    return response.data
  },

  getById: async (id: string): Promise<User> => {
    const response = await api.get(`/users/${id}`)
    return response.data
  },

  updateRole: async (id: string, role: string): Promise<User> => {
    const response = await api.put(`/users/${id}/role?role=${role}`)
    return response.data
  }
}
