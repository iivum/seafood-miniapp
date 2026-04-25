<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  Dashboard,
  Goods,
  ShoppingCart,
  User,
  Setting,
  Fold,
  Expand,
  HomeFilled,
  Logout
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const isCollapsed = ref(false)

const activeMenu = computed(() => route.name as string)

const menuItems = [
  { name: 'dashboard', label: '数据概览', icon: Dashboard },
  { name: 'products', label: '商品管理', icon: Goods },
  { name: 'orders', label: '订单管理', icon: ShoppingCart },
  { name: 'users', label: '用户管理', icon: User },
  { name: 'config', label: '配置中心', icon: Setting }
]

function handleMenuSelect(name: string) {
  router.push({ name })
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}
</script>

<template>
  <div class="main-layout">
    <!-- Sidebar -->
    <aside class="sidebar" :class="{ 'sidebar--collapsed': isCollapsed }">
      <!-- Logo area -->
      <div class="sidebar__logo">
        <el-icon class="sidebar__logo-icon"><span class="emoji">🐠</span></el-icon>
        <transition name="fade">
          <div v-if="!isCollapsed" class="sidebar__logo-text">
            <span class="sidebar__logo-title">大海的味道</span>
            <span class="sidebar__logo-subtitle">商家管理后台</span>
          </div>
        </transition>
      </div>

      <!-- Navigation -->
      <nav class="sidebar__nav">
        <div
          v-for="item in menuItems"
          :key="item.name"
          class="sidebar__menu-item"
          :class="{ 'sidebar__menu-item--active': activeMenu === item.name }"
          @click="handleMenuSelect(item.name)"
        >
          <el-icon class="sidebar__menu-icon">
            <component :is="item.icon" />
          </el-icon>
          <transition name="fade">
            <span v-if="!isCollapsed" class="sidebar__menu-label">{{ item.label }}</span>
          </transition>
        </div>
      </nav>

      <!-- Collapse toggle -->
      <div class="sidebar__footer">
        <div class="sidebar__collapse-btn" @click="toggleCollapse">
          <el-icon>
            <Expand v-if="isCollapsed" />
            <Fold v-else />
          </el-icon>
          <transition name="fade">
            <span v-if="!isCollapsed">收起</span>
          </transition>
        </div>
      </div>
    </aside>

    <!-- Main content area -->
    <div class="main-content">
      <!-- Header -->
      <header class="header">
        <div class="header__left">
          <el-button
            :icon="HomeFilled"
            text
            @click="router.push('/dashboard')"
            class="header__home-btn"
          >
            首页
          </el-button>
        </div>
        <div class="header__right">
          <span class="header__username">{{ authStore.username }}</span>
          <el-button
            :icon="Logout"
            text
            @click="handleLogout"
            class="header__logout-btn"
          >
            退出
          </el-button>
        </div>
      </header>

      <!-- Page content -->
      <main class="page-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.main-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* Sidebar styles */
.sidebar {
  width: var(--sidebar-width);
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-normal);
  flex-shrink: 0;
}

.sidebar--collapsed {
  width: 64px;
}

.sidebar__logo {
  display: flex;
  align-items: center;
  padding: 16px;
  gap: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  min-height: 56px;
}

.sidebar__logo-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.sidebar__logo-text {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar__logo-title {
  font-size: 1rem;
  font-weight: 800;
  color: #fff;
  white-space: nowrap;
}

.sidebar__logo-subtitle {
  font-size: 0.7rem;
  color: var(--sidebar-text-muted);
  white-space: nowrap;
}

.sidebar__nav {
  flex: 1;
  padding: 12px 8px;
  overflow-y: auto;
}

.sidebar__menu-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  margin-bottom: 4px;
  border-radius: var(--radius-md);
  color: var(--sidebar-text);
  cursor: pointer;
  transition: all var(--transition-fast);
  gap: 12px;
}

.sidebar__menu-item:hover {
  background: var(--sidebar-hover);
}

.sidebar__menu-item--active {
  background: var(--sidebar-active);
  color: var(--color-primary);
}

.sidebar__menu-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.sidebar__menu-label {
  font-size: 0.9rem;
  font-weight: 500;
  white-space: nowrap;
}

.sidebar__footer {
  padding: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.sidebar__collapse-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  color: var(--sidebar-text-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
  font-size: 0.8rem;
}

.sidebar__collapse-btn:hover {
  background: var(--sidebar-hover);
  color: var(--sidebar-text);
}

/* Main content styles */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--content-bg);
}

/* Header styles */
.header {
  height: var(--header-height);
  background: var(--card-bg);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
}

.header__left {
  display: flex;
  align-items: center;
}

.header__home-btn {
  color: var(--text-secondary);
}

.header__home-btn:hover {
  color: var(--color-primary);
}

.header__right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header__username {
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.header__logout-btn {
  color: var(--text-muted);
}

.header__logout-btn:hover {
  color: var(--color-danger);
}

/* Page content */
.page-content {
  flex: 1;
  overflow-y: auto;
  padding: 28px 32px;
}

/* Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* Responsive */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    z-index: 100;
    transform: translateX(-100%);
  }

  .sidebar--open {
    transform: translateX(0);
  }

  .page-content {
    padding: 16px;
  }
}
</style>
