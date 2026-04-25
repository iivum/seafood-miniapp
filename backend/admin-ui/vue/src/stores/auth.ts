import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api/axios'

export const useAuthStore = defineStore('auth', () => {
  const username = ref<string | null>(null)

  const isAuthenticated = ref(false)

  function setAuth(newUsername: string) {
    username.value = newUsername
    isAuthenticated.value = true
  }

  function logout() {
    username.value = null
    isAuthenticated.value = false
  }

  async function login(user: string, pass: string): Promise<boolean> {
    try {
      await api.post('/auth/login', { username: user, password: pass })
      setAuth(user)
      return true
    } catch {
      return false
    }
  }

  return {
    username,
    isAuthenticated,
    setAuth,
    logout,
    login
  }
})
