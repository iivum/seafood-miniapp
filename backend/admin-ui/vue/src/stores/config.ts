import { defineStore } from 'pinia'
import { ref } from 'vue'
import { configApi } from '@/api/config'
import type { ConfigProperty, SaveConfigRequest } from '@/types'

export const useConfigStore = defineStore('config', () => {
  const configs = ref<ConfigProperty[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchConfigs(serviceName: string, profile: string, label: string = 'main') {
    loading.value = true
    error.value = null
    try {
      configs.value = await configApi.getProperties(serviceName, profile, label)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '获取配置列表失败'
    } finally {
      loading.value = false
    }
  }

  async function saveConfig(data: SaveConfigRequest): Promise<boolean> {
    try {
      await configApi.save(data)
      await fetchConfigs(data.serviceName, data.profile, data.label)
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '保存配置失败'
      return false
    }
  }

  async function deleteConfig(serviceName: string, profile: string, label: string, key: string): Promise<boolean> {
    try {
      await configApi.delete(serviceName, profile, label, key)
      await fetchConfigs(serviceName, profile, label)
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '删除配置失败'
      return false
    }
  }

  async function encryptValue(plaintext: string): Promise<string | null> {
    try {
      const response = await configApi.encrypt(plaintext)
      return response.encrypted
    } catch (e) {
      error.value = e instanceof Error ? e.message : '加密失败'
      return null
    }
  }

  return {
    configs,
    loading,
    error,
    fetchConfigs,
    saveConfig,
    deleteConfig,
    encryptValue
  }
})
