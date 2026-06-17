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
    // v2.1 修:Sprint 1 closure 调 /api/orders(mp 端 user-only)→ admin 访问 403。
    // 改 /api/admin/orders(AdminOrderController.list 应暴露 — 但当前 backend
    // 缺 GET /api/admin/orders 端点,只有 batch-ship / export / print-picklist)。
    // 见 src/__tests__/order-list-endpoint.test.tsx 防回归。
    const res = await api.get<PageResponse<OrderResponse>>(`/admin/orders?${search.toString()}`);
    return res.data;
  },
  detail: async (id: string): Promise<OrderDetailResponse> => {
    const res = await api.get<OrderDetailResponse>(`/admin/orders/${id}/detail`);
    return res.data;
  },
  ship: async (id: string): Promise<OrderResponse> => {
    // v2.1 修:同 list(),改 admin 路径(后端 AdminOrderController.ship 不存在,
    // 但 /api/orders/{id}/ship 是 mp 端 user-only,admin 必 403;后续 sprint 2
    // 修 backend 加 AdminOrderController.ship 或转发到 OrderService)
    const res = await api.post<OrderResponse>(`/admin/orders/${id}/ship`);
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
