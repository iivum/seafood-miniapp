<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { ConfigProperty } from '@/types'

const props = defineProps<{
  visible: boolean
  config: ConfigProperty | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'save', config: ConfigProperty): void
  (e: 'encrypt', plaintext: string): Promise<string | null>
  (e: 'close'): void
}>()

const form = ref<ConfigProperty>({
  serviceName: 'gateway',
  profile: 'docker',
  label: 'main',
  key: '',
  value: '',
  encrypted: false
})

const formRef = ref()
const loading = ref(false)
const encrypting = ref(false)

const isEdit = computed(() => !!props.config?.key)
const title = computed(() => isEdit.value ? '编辑配置' : '添加配置')

const rules = {
  serviceName: [{ required: true, message: '请选择服务', trigger: 'change' }],
  profile: [{ required: true, message: '请选择环境', trigger: 'change' }],
  key: [{ required: true, message: '请输入配置项', trigger: 'blur' }],
  value: [{ required: true, message: '请输入配置值', trigger: 'blur' }]
}

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

watch(() => props.visible, (newVal) => {
  if (newVal && props.config) {
    form.value = { ...props.config }
  } else if (newVal) {
    form.value = {
      serviceName: 'gateway',
      profile: 'docker',
      label: 'main',
      key: '',
      value: '',
      encrypted: false
    }
  }
})

function handleClose() {
  emit('update:visible', false)
  emit('close')
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
    loading.value = true
    emit('save', { ...form.value })
  } catch {
    // Validation failed
  } finally {
    loading.value = false
  }
}

async function handleEncrypt() {
  if (!form.value.value) {
    ElMessage.warning('请先输入要加密的值')
    return
  }

  encrypting.value = true
  try {
    const encrypted = await emit('encrypt', form.value.value)
    if (encrypted) {
      form.value.value = encrypted
      form.value.encrypted = true
      ElMessage.success('加密成功')
    }
  } finally {
    encrypting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="550px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      class="config-form"
    >
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="服务" prop="serviceName">
            <el-select
              v-model="form.serviceName"
              placeholder="选择服务"
              style="width: 100%"
              :disabled="isEdit"
            >
              <el-option
                v-for="service in serviceOptions"
                :key="service"
                :label="service"
                :value="service"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="环境" prop="profile">
            <el-select
              v-model="form.profile"
              placeholder="选择环境"
              style="width: 100%"
              :disabled="isEdit"
            >
              <el-option
                v-for="option in profileOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="配置项" prop="key">
        <el-input
          v-model="form.key"
          placeholder="如: spring.datasource.url"
          :disabled="isEdit"
        />
      </el-form-item>

      <el-form-item label="配置值" prop="value">
        <el-input
          v-model="form.value"
          type="textarea"
          :rows="3"
          placeholder="请输入配置值"
        />
      </el-form-item>

      <el-form-item label="加密">
        <el-switch v-model="form.encrypted" />
        <el-button
          text
          type="primary"
          style="margin-left: 12px;"
          :loading="encrypting"
          @click="handleEncrypt"
        >
          加密值
        </el-button>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.config-form {
  padding: 0 8px;
}
</style>
