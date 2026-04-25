import { defineStore } from 'pinia'
import { ref } from 'vue'
import { productApi } from '@/api/product'
import type { Product, CreateProductRequest } from '@/types'

export const useProductStore = defineStore('product', () => {
  const products = ref<Product[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchProducts() {
    loading.value = true
    error.value = null
    try {
      products.value = await productApi.getAll()
    } catch (e) {
      error.value = e instanceof Error ? e.message : '获取商品列表失败'
    } finally {
      loading.value = false
    }
  }

  async function createProduct(data: CreateProductRequest): Promise<boolean> {
    try {
      await productApi.create(data)
      await fetchProducts()
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '创建商品失败'
      return false
    }
  }

  async function updateProduct(id: string, data: CreateProductRequest): Promise<boolean> {
    try {
      await productApi.update(id, data)
      await fetchProducts()
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '更新商品失败'
      return false
    }
  }

  async function deleteProduct(id: string): Promise<boolean> {
    try {
      await productApi.delete(id)
      await fetchProducts()
      return true
    } catch (e) {
      error.value = e instanceof Error ? e.message : '删除商品失败'
      return false
    }
  }

  return {
    products,
    loading,
    error,
    fetchProducts,
    createProduct,
    updateProduct,
    deleteProduct
  }
})
