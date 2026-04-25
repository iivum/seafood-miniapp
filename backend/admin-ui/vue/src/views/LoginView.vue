<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()

const loginForm = ref({
  username: '',
  password: ''
})

const loading = ref(false)
const errorMessage = ref('')

async function handleLogin() {
  if (!loginForm.value.username || !loginForm.value.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const success = await authStore.login(loginForm.value.username, loginForm.value.password)
    if (success) {
      ElMessage.success('登录成功')
      router.push('/dashboard')
    } else {
      errorMessage.value = '用户名或密码错误'
    }
  } catch {
    errorMessage.value = '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="login-bg__gradient"></div>
      <div class="login-bg__pattern"></div>
    </div>

    <div class="login-container">
      <div class="login-card">
        <!-- Logo -->
        <div class="login-card__header">
          <div class="login-logo">
            <span class="login-logo__emoji">🐠</span>
            <div class="login-logo__text">
              <span class="login-logo__title">大海的味道</span>
              <span class="login-logo__subtitle">商家管理后台</span>
            </div>
          </div>
        </div>

        <!-- Form -->
        <el-form
          :model="loginForm"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item>
            <el-input
              v-model="loginForm.username"
              placeholder="用户名"
              size="large"
              :prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item>
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="密码"
              size="large"
              :prefix-icon="Lock"
              autocomplete="current-password"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-alert
            v-if="errorMessage"
            :title="errorMessage"
            type="error"
            show-icon
            :closable="false"
            class="login-error"
          />

          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form>

              </div>
    </div>
  </div>
</template>

<script lang="ts">
import { User, Lock } from '@element-plus/icons-vue'
export default {
  components: { User, Lock }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.login-bg__gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #1A1A2E 0%, #2D3452 50%, #4ECDC4 100%);
}

.login-bg__pattern {
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle at 25% 25%, rgba(255, 255, 255, 0.05) 0%, transparent 50%),
    radial-gradient(circle at 75% 75%, rgba(255, 255, 255, 0.05) 0%, transparent 50%);
}

.login-container {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 400px;
  padding: 20px;
}

.login-card {
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 40px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.login-card__header {
  text-align: center;
  margin-bottom: 32px;
}

.login-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.login-logo__emoji {
  font-size: 2.5rem;
}

.login-logo__text {
  display: flex;
  flex-direction: column;
  text-align: left;
}

.login-logo__title {
  font-size: 1.25rem;
  font-weight: 800;
  color: #fff;
}

.login-logo__subtitle {
  font-size: 0.8rem;
  color: rgba(255, 255, 255, 0.7);
}

.login-form {
  margin-bottom: 24px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-form :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: none;
  border-radius: 10px;
  padding: 4px 16px;
}

.login-form :deep(.el-input__wrapper:hover),
.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(78, 205, 196, 0.2);
}

.login-form :deep(.el-input__inner) {
  color: #fff;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.5);
}

.login-form :deep(.el-input__prefix) {
  color: rgba(255, 255, 255, 0.5);
}

.login-error {
  margin-bottom: 20px;
  border-radius: 10px;
}

.login-btn {
  width: 100%;
  height: 48px;
  font-size: 1rem;
  font-weight: 600;
  border-radius: 10px;
  background: linear-gradient(135deg, #4ECDC4 0%, #6FE3DB 100%);
  border: none;
  box-shadow: 0 4px 15px rgba(78, 205, 196, 0.3);
  transition: all 0.3s ease;
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(78, 205, 196, 0.4);
}

.login-btn:active {
  transform: translateY(0);
}

.login-card__footer {
  text-align: center;
}

.login-hint {
  font-size: 0.75rem;
  color: rgba(255, 255, 255, 0.5);
}
</style>
