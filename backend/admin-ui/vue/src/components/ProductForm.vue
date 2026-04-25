<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { Product } from '@/types'

const props = defineProps<{
  visible: boolean
  product: Product | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'save', product: Product): void
  (e: 'close'): void
}>()

const form = ref<Product>({
  name: '',
  description: '',
  price: 0,
  stock: 0,
  category: '',
  imageUrl: '',
  onSale: true
})

const formRef = ref()
const loading = ref(false)

const isEdit = computed(() => !!props.product?.id)
const title = computed(() => isEdit.value ? '编辑商品' : '添加商品')

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  category: [{ required: true, message: '请输入商品分类', trigger: 'blur' }],
  price: [
    { required: true, message: '请输入商品价格', trigger: 'blur' },
    { type: 'number', min: 0, message: '价格必须大于等于0', trigger: 'blur' }
  ],
  stock: [
    { required: true, message: '请输入商品库存', trigger: 'blur' },
    { type: 'number', min: 0, message: '库存必须大于等于0', trigger: 'blur' }
  ]
}

watch(() => props.visible, (newVal) => {
  if (newVal && props.product) {
    form.value = { ...props.product }
  } else if (newVal) {
    form.value = {
      name: '',
      description: '',
      price: 0,
      stock: 0,
      category: '',
      imageUrl: '',
      onSale: true
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
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="500px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="80px"
      class="product-form"
    >
      <el-form-item label="商品名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入商品名称" />
      </el-form-item>

      <el-form-item label="商品分类" prop="category">
        <el-input v-model="form.category" placeholder="如：海鲜、鱼类、虾类" />
      </el-form-item>

      <el-form-item label="价格" prop="price">
        <el-input-number
          v-model="form.price"
          :precision="2"
          :min="0"
          :step="0.1"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="库存" prop="stock">
        <el-input-number
          v-model="form.stock"
          :min="0"
          :step="1"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="图片URL" prop="imageUrl">
        <el-input v-model="form.imageUrl" placeholder="请输入商品图片URL" />
      </el-form-item>

      <el-form-item label="商品描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="请输入商品描述"
        />
      </el-form-item>

      <el-form-item label="上架">
        <el-switch v-model="form.onSale" />
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
.product-form {
  padding: 0 8px;
}
</style>
