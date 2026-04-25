import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userApi } from '@/api/user'
import type { User } from '@/types'

export const useUserStore = defineStore('user', () => {
  const users = ref<User[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchUsers() {
    loading.value = true
    error.value = null
    try {
      users.value = await userApi.getAll()
    } catch (e) {
      error.value = e instanceof Error ? e.message : '获取用户列表失败'
    } finally {
      loading.value = false
    }
  }

  async function updateUserRole(id: string, role: string): Promise<boolean> {
    try {
      await userApi.updateRole(id, role)
      await fetchUsers()
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '更新用户角色失败'
      return false
    }
  }

  return {
    users,
    loading,
    error,
    fetchUsers,
    updateUserRole
  }
})
