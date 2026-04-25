<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useConfigStore } from '@/stores/config'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConfigForm from '@/components/ConfigForm.vue'
import type { ConfigProperty } from '@/types'

const configStore = useConfigStore()

const serviceName = ref('gateway')
const profile = ref('docker')
const searchQuery = ref('')
const showForm = ref(false)
const editingConfig = ref<ConfigProperty | null>(null)

const serviceOptions = [
  'gateway',
  'product-service',
  'order-service',
  'user-service',
  'admin-ui',
  'application'
]

const profileOptions = [
  { label: 'Docker', value: 'docker' },
  { label: 'Native', value: 'native' },
  { label: '开发环境', value: 'dev' },
  { label: '生产环境', value: 'prod' }
]

const filteredConfigs = computed(() => {
  if (!searchQuery.value) return configStore.configs
  const query = searchQuery.value.toLowerCase()
  return configStore.configs.filter(c =>
    c.key?.toLowerCase().includes(query)
  )
})

function handleAdd() {
  editingConfig.value = {
    serviceName: serviceName.value,
    profile: profile.value,
    label: 'main',
    key: '',
    value: '',
    encrypted: false
  }
  showForm.value = true
}

function handleEdit(config: ConfigProperty) {
  editingConfig.value = { ...config }
  showForm.value = true
}

function handleCloseForm() {
  showForm.value = false
  editingConfig.value = null
}

async function handleSave(config: ConfigProperty) {
  const success = await configStore.saveConfig({
    serviceName: config.serviceName,
    profile: config.profile,
    label: config.label,
    key: config.key,
    value: config.value,
    encrypted: config.encrypted
  })

  if (success) {
    ElMessage.success('配置保存成功')
    handleCloseForm()
  } else {
    ElMessage.error(configStore.error || '保存失败')
  }
}

async function handleDelete(config: ConfigProperty) {
  try {
    await ElMessageBox.confirm(
      `确定要删除配置项 "${config.key}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const success = await configStore.deleteConfig(
      config.serviceName,
      config.profile,
      config.label,
      config.key
    )

    if (success) {
      ElMessage.success('配置已删除')
      handleCloseForm()
    } else {
      ElMessage.error(configStore.error || '删除失败')
    }
  } catch {
    // User cancelled
  }
}

async function handleEncrypt(plaintext: string): Promise<string | null> {
  return await configStore.encryptValue(plaintext)
}

function refresh() {
  configStore.fetchConfigs(serviceName.value, profile.value)
}

watch([serviceName, profile], () => {
  refresh()
})

onMounted(refresh)
</script>

<template>
  <div class="config-list">
    <!-- Page header -->
    <div class="page-header">
      <div>
        <h1 class="page-header__title">配置中心</h1>
        <p class="page-header__subtitle">管理微服务配置，支持加密配置项</p>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="toolbar">
      <el-select
        v-model="serviceName"
        placeholder="选择服务"
        style="width: 160px;"
      >
        <el-option
          v-for="service in serviceOptions"
          :key="service"
          :label="service"
          :value="service"
        />
      </el-select>

      <el-select
        v-model="profile"
        placeholder="选择环境"
        style="width: 120px;"
      >
        <el-option
          v-for="option in profileOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>

      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        添加配置
      </el-button>

      <el-button @click="refresh">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>

      <el-input
        v-model="searchQuery"
        placeholder="搜索配置项..."
        clearable
        style="max-width: 200px;"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <!-- Loading state -->
    <el-skeleton :loading="configStore.loading" animated>
      <!-- Configs table -->
      <el-table
        :data="filteredConfigs"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="key" label="配置项" min-width="200" />
        <el-table-column label="值" min-width="300">
          <template #default="{ row }">
            <template v-if="row.encrypted">
              <span class="encrypted-value">****** (已加密)</span>
            </template>
            <template v-else>
              <span class="config-value">{{ row.value }}</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="加密" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.encrypted" type="warning" size="small">是</el-tag>
            <el-tag v-else type="info" size="small">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-skeleton>

    <!-- Config form dialog -->
    <ConfigForm
      v-model:visible="showForm"
      :config="editingConfig"
      @save="handleSave"
      @encrypt="handleEncrypt"
      @close="handleCloseForm"
    />
  </div>
</template>

<script lang="ts">
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
export default {
  components: { Plus, Refresh, Search }
}
</script>

<style scoped>
.config-list {
  max-width: 1600px;
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
  flex-wrap: wrap;
}

.config-value {
  font-family: monospace;
  word-break: break-all;
}

.encrypted-value {
  color: var(--text-muted);
  font-style: italic;
}
</style>
