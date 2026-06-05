import { api } from '@/lib/api';
import type { PageResponse, ProductRequest, ProductResponse, ProductStatsResponse } from '@/types/api';

interface ListParams {
  page?: number;
  size?: number;
  category?: string;
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
  stats: async (): Promise<ProductStatsResponse> => {
    const res = await api.get<ProductStatsResponse>('/admin/products/stats');
    return res.data;
  },
};
