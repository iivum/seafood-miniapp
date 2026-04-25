<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useProductStore } from '@/stores/product'
import { ElMessage, ElMessageBox } from 'element-plus'
import ProductForm from '@/components/ProductForm.vue'
import type { Product } from '@/types'

const productStore = useProductStore()

const searchQuery = ref('')
const showForm = ref(false)
const editingProduct = ref<Product | null>(null)

const filteredProducts = computed(() => {
  if (!searchQuery.value) return productStore.products
  const query = searchQuery.value.toLowerCase()
  return productStore.products.filter(p =>
    p.name?.toLowerCase().includes(query) ||
    p.category?.toLowerCase().includes(query) ||
    p.id?.toLowerCase().includes(query)
  )
})

function handleAdd() {
  editingProduct.value = {
    name: '',
    description: '',
    price: 0,
    stock: 0,
    category: '',
    imageUrl: '',
    onSale: true
  }
  showForm.value = true
}

function handleEdit(product: Product) {
  editingProduct.value = { ...product }
  showForm.value = true
}

function handleCloseForm() {
  showForm.value = false
  editingProduct.value = null
}

async function handleSave(product: Product) {
  const isNew = !product.id
  let success: boolean

  if (isNew) {
    success = await productStore.createProduct(product)
  } else {
    success = await productStore.updateProduct(product.id!, product)
  }

  if (success) {
    ElMessage.success(isNew ? '商品创建成功' : '商品更新成功')
    handleCloseForm()
  } else {
    ElMessage.error(productStore.error || '操作失败')
  }
}

async function handleDelete(product: Product) {
  try {
    await ElMessageBox.confirm(
      `确定要删除商品 "${product.name}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const success = await productStore.deleteProduct(product.id!)
    if (success) {
      ElMessage.success('商品已删除')
      handleCloseForm()
    } else {
      ElMessage.error(productStore.error || '删除失败')
    }
  } catch {
    // User cancelled
  }
}

function formatPrice(price: number): string {
  return '¥' + price.toFixed(2)
}

onMounted(() => {
  productStore.fetchProducts()
})
</script>

<template>
  <div class="product-list">
    <!-- Page header -->
    <div class="page-header">
      <div>
        <h1 class="page-header__title">商品管理</h1>
        <p class="page-header__subtitle">管理商城商品，添加、编辑或删除商品</p>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="toolbar">
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        添加商品
      </el-button>
      <el-input
        v-model="searchQuery"
        placeholder="搜索商品名称、分类或ID..."
        clearable
        style="max-width: 300px;"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <!-- Loading state -->
    <el-skeleton :loading="productStore.loading" animated>
      <!-- Products table -->
      <el-table
        :data="filteredProducts"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="id" label="商品ID" width="220" />
        <el-table-column prop="name" label="商品名称" min-width="150" />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column label="价格" width="100">
          <template #default="{ row }">
            {{ formatPrice(row.price) }}
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80">
          <template #default="{ row }">
            <span :class="{ 'low-stock': row.stock < 10 }">
              {{ row.stock }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="上架" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.onSale" type="success" size="small">是</el-tag>
            <el-tag v-else type="info" size="small">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-skeleton>

    <!-- Product form dialog -->
    <ProductForm
      v-model:visible="showForm"
      :product="editingProduct"
      @save="handleSave"
      @close="handleCloseForm"
    />
  </div>
</template>

<script lang="ts">
import { Plus, Search } from '@element-plus/icons-vue'
export default {
  components: { Plus, Search }
}
</script>

<style scoped>
.product-list {
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
  flex-wrap: wrap;
}

.low-stock {
  color: #f57f17;
  font-weight: 600;
}
</style>
