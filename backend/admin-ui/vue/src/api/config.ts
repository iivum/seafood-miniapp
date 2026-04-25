import api from './axios'
import type { ConfigProperty, SaveConfigRequest } from '@/types'

export const configApi = {
  getProperties: async (serviceName: string, profile: string, label: string): Promise<ConfigProperty[]> => {
    const response = await api.get(`/config/${serviceName}/${profile}/${label}`)
    return response.data
  },

  save: async (data: SaveConfigRequest): Promise<void> => {
    await api.post('/config', data)
  },

  delete: async (serviceName: string, profile: string, label: string, key: string): Promise<void> => {
    await api.delete(`/config/${serviceName}/${profile}/${label}/${key}`)
  },

  encrypt: async (plaintext: string): Promise<{ encrypted: string }> => {
    const response = await api.post('/config/encrypt', { plaintext })
    return response.data
  }
}
