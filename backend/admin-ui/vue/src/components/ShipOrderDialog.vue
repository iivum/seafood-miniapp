<script setup lang="ts">
import { ref, watch } from 'vue'
import type { Order } from '@/types'

const props = defineProps<{
  visible: boolean
  order: Order | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', data: { carrierCode: string; carrierName: string; trackingNumber: string }): void
  (e: 'close'): void
}>()

const form = ref({
  carrierCode: '',
  carrierName: '',
  trackingNumber: ''
})

const formRef = ref()
const loading = ref(false)

const carrierOptions = [
  { label: '顺丰速运', value: 'SF' },
  { label: '中通快递', value: 'ZTO' },
  { label: '圆通速递', value: 'YTO' },
  { label: '韵达快递', value: 'YD' },
  { label: '申通快递', value: 'STO' },
  { label: '京东物流', value: 'JD' },
  { label: '邮政EMS', value: 'EMS' },
  { label: '其他', value: 'OTHER' }
]

const rules = {
  carrierCode: [{ required: true, message: '请选择快递公司', trigger: 'change' }],
  carrierName: [{ required: true, message: '请输入快递公司名称', trigger: 'blur' }],
  trackingNumber: [{ required: true, message: '请输入运单号', trigger: 'blur' }]
}

watch(() => props.visible, (newVal) => {
  if (newVal) {
    form.value = {
      carrierCode: '',
      carrierName: '',
      trackingNumber: ''
    }
  }
})

watch(() => form.value.carrierCode, (newVal) => {
  const carrier = carrierOptions.find(c => c.value === newVal)
  if (carrier && !form.value.carrierName) {
    form.value.carrierName = carrier.label
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
    emit('confirm', { ...form.value })
  } catch {
    // Validation failed
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="订单发货"
    width="450px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
    @close="handleClose"
  >
    <div v-if="order" class="ship-form">
      <el-alert
        title="订单信息"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 20px;"
      >
        <template #default>
          订单号: {{ order.orderNumber }}<br>
          用户: {{ order.userId }}
        </template>
      </el-alert>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="快递公司" prop="carrierCode">
          <el-select
            v-model="form.carrierCode"
            placeholder="请选择快递公司"
            style="width: 100%"
          >
            <el-option
              v-for="carrier in carrierOptions"
              :key="carrier.value"
              :label="carrier.label"
              :value="carrier.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="快递名称" prop="carrierName">
          <el-input v-model="form.carrierName" placeholder="请输入快递公司名称" />
        </el-form-item>

        <el-form-item label="运单号" prop="trackingNumber">
          <el-input
            v-model="form.trackingNumber"
            placeholder="请输入运单号"
            clearable
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        确认发货
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.ship-form {
  padding: 0 8px;
}
</style>
