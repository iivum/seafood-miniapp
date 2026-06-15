import { api } from '@/lib/api';
import type {
  BatchShipRequest,
  BatchShipResponse,
  OrderDetailResponse,
  OrderResponse,
  PageResponse,
} from '@/types/api';

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
  /**
   * 4.13:批量发货。request orderIds 1..50;返回 successIds / failed / 统计计数。
   * 后端 4.13 设计就是 partial success:返回 200,UI 看 successCount / failedCount
   * 决定弹哪种 toast。
   */
  batchShip: async (body: BatchShipRequest): Promise<BatchShipResponse> => {
    const res = await api.post<BatchShipResponse>('/admin/orders/batch-ship', body);
    return res.data;
  },
  /**
   * 4.15:导出订单 CSV。responseType 'blob' → 浏览器自动下载
   * (Content-Disposition 已由后端带 attachment + filename)。
   */
  exportCsv: async (): Promise<Blob> => {
    const res = await api.get<Blob>('/admin/orders/export', { responseType: 'blob' });
    return res.data;
  },
};
