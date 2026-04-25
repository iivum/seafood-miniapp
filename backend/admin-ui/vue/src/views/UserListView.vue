<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import type { User } from '@/types'

const userStore = useUserStore()

const searchQuery = ref('')

const filteredUsers = computed(() => {
  if (!searchQuery.value) return userStore.users
  const query = searchQuery.value.toLowerCase()
  return userStore.users.filter(u =>
    u.nickname?.toLowerCase().includes(query) ||
    u.openId?.toLowerCase().includes(query) ||
    u.id?.toLowerCase().includes(query)
  )
})

function formatRole(role: string): string {
  const map: Record<string, string> = {
    ADMIN: '管理员',
    USER: '普通用户',
    MERCHANT: '商户'
  }
  return map[role] || role
}

function getRoleType(role: string): string {
  const map: Record<string, string> = {
    ADMIN: 'danger',
    USER: '',
    MERCHANT: 'warning'
  }
  return map[role] || 'info'
}

async function handleSetAdmin(user: User) {
  const success = await userStore.updateUserRole(user.id, 'ADMIN')
  if (success) {
    ElMessage.success(`用户 ${user.nickname} 已设为管理员`)
  } else {
    ElMessage.error(userStore.error || '设置失败')
  }
}

async function handleRemoveAdmin(user: User) {
  const success = await userStore.updateUserRole(user.id, 'USER')
  if (success) {
    ElMessage.success(`用户 ${user.nickname} 已取消管理员`)
  } else {
    ElMessage.error(userStore.error || '操作失败')
  }
}

onMounted(() => {
  userStore.fetchUsers()
})
</script>

<template>
  <div class="user-list">
    <!-- Page header -->
    <div class="page-header">
      <div>
        <h1 class="page-header__title">用户管理</h1>
        <p class="page-header__subtitle">查看和管理商城用户</p>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="toolbar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索用户昵称/OpenID..."
        clearable
        style="max-width: 300px;"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <!-- Loading state -->
    <el-skeleton :loading="userStore.loading" animated>
      <!-- Users table -->
      <el-table
        :data="filteredUsers"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="id" label="用户ID" width="180" show-overflow-tooltip />
        <el-table-column label="头像" width="70">
          <template #default="{ row }">
            <el-avatar
              v-if="row.avatarUrl"
              :src="row.avatarUrl"
              :size="40"
              shape="circle"
            />
            <el-avatar v-else :size="40" shape="circle">
              {{ row.nickname?.charAt(0) || '无' }}
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="openId" label="OpenID" width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="120" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="getRoleType(row.role)" size="small">
              {{ formatRole(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.role !== 'ADMIN'"
              link
              type="primary"
              @click="handleSetAdmin(row)"
            >
              设为管理员
            </el-button>
            <el-button
              v-if="row.role === 'ADMIN'"
              link
              type="danger"
              @click="handleRemoveAdmin(row)"
            >
              取消管理员
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-skeleton>
  </div>
</template>

<script lang="ts">
import { Search } from '@element-plus/icons-vue'
export default {
  components: { Search }
}
</script>

<style scoped>
.user-list {
  max-width: 1400px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header__title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.page-header__subtitle {
  color: var(--text-muted);
  font-size: 0.9rem;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
</style>
