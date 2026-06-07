import { api } from '@/lib/api';
import type { OrderDetailResponse, OrderResponse, PageResponse } from '@/types/api';

interface ListParams {
  page?: number;
  size?: number;
}

export const ordersApi = {
  list: async (params: ListParams = {}): Promise<PageResponse<OrderResponse>> => {
    const search = new URLSearchParams();
    if (params.page !== undefined) {
      search.set('page', String(params.page));
    }
    if (params.size !== undefined) {
      search.set('size', String(params.size));
    }
    const res = await api.get<PageResponse<OrderResponse>>(`/orders?${search.toString()}`);
    return res.data;
  },
  detail: async (id: string): Promise<OrderDetailResponse> => {
    const res = await api.get<OrderDetailResponse>(`/admin/orders/${id}/detail`);
    return res.data;
  },
  ship: async (id: string): Promise<OrderResponse> => {
    const res = await api.post<OrderResponse>(`/orders/${id}/ship`);
    return res.data;
  },
};
