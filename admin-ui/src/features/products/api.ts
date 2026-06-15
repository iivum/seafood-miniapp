import { api } from '@/lib/api';
import type { PageResponse, ProductRequest, ProductResponse, ProductStatsResponse, ProductStatus } from '@/types/api';

interface ListParams {
  page?: number;
  size?: number;
  category?: string;
  status?: ProductStatus;
}

interface BatchStatusRequest {
  ids: string[];
  status: ProductStatus;
}

interface BatchStatusResponse {
  total: number;
  successCount: number;
  failedCount: number;
  successIds: string[];
  failed: Array<{ productId: string; reason: string }>;
}

export const productsApi = {
  list: async (params: ListParams = {}): Promise<PageResponse<ProductResponse>> => {
    const search = new URLSearchParams();
    if (params.page !== undefined) {
      search.set('page', String(params.page));
    }
    if (params.size !== undefined) {
      search.set('size', String(params.size));
    }
    if (params.category) {
      search.set('category', params.category);
    }
    if (params.status) {
      search.set('status', params.status);
    }
    const res = await api.get<PageResponse<ProductResponse>>(`/products?${search.toString()}`);
    return res.data;
  },
  get: async (id: string): Promise<ProductResponse> => {
    const res = await api.get<ProductResponse>(`/products/${id}`);
    return res.data;
  },
  create: async (body: ProductRequest): Promise<ProductResponse> => {
    const res = await api.post<ProductResponse>('/products', body);
    return res.data;
  },
  update: async (id: string, body: ProductRequest): Promise<ProductResponse> => {
    const res = await api.put<ProductResponse>(`/products/${id}`, body);
    return res.data;
  },
  delete: async (id: string): Promise<void> => {
    await api.delete(`/products/${id}`);
  },
  discontinue: async (id: string): Promise<ProductResponse> => {
    const res = await api.post<ProductResponse>(`/products/${id}/discontinue`);
    return res.data;
  },
  /** 3.1 复制商品(已接 AdminProductController.duplicate)。 */
  duplicate: async (id: string): Promise<ProductResponse> => {
    const res = await api.post<ProductResponse>(`/admin/products/${id}/duplicate`);
    return res.data;
  },
  /** 3.3 批量状态变更(走 AdminProductController.batchStatus)。 */
  batchStatus: async (body: BatchStatusRequest): Promise<BatchStatusResponse> => {
    const res = await api.post<BatchStatusResponse>('/admin/products/batch-status', body);
    return res.data;
  },
  stats: async (): Promise<ProductStatsResponse> => {
    const res = await api.get<ProductStatsResponse>('/admin/products/stats');
    return res.data;
  },
};
