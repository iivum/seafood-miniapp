import { api } from '@/lib/api';
import type { PageResponse, RefundResponse, RefundStatusCode } from '@/types/api';

interface ListParams {
  status?: RefundStatusCode;
  page?: number;
  size?: number;
}

/**
 * 4.11:退款审核 API(对应后端 4.8 / AdminRefundController)。
 */
export const refundsApi = {
  /**
   * 4.11:按状态分页列退款单 — GET /api/admin/refunds?status=REQUESTED
   * 不传 status 拉全量(ADMIN 审核面板顶部 tab 用)。
   */
  list: async (params: ListParams = {}): Promise<PageResponse<RefundResponse>> => {
    const search = new URLSearchParams();
    if (params.status !== undefined) search.set('status', params.status);
    if (params.page !== undefined) search.set('page', String(params.page));
    if (params.size !== undefined) search.set('size', String(params.size));
    const qs = search.toString();
    const res = await api.get<PageResponse<RefundResponse>>(
      `/admin/refunds${qs ? '?' + qs : ''}`
    );
    return res.data;
  },

  /**
   * 4.8:同意退款 — POST /api/admin/refunds/{id}/approve
   */
  approve: async (id: string): Promise<RefundResponse> => {
    const res = await api.post<RefundResponse>(`/admin/refunds/${encodeURIComponent(id)}/approve`);
    return res.data;
  },

  /**
   * 4.8:拒绝退款 — POST /api/admin/refunds/{id}/reject?reason=...
   */
  reject: async (id: string, reason: string): Promise<RefundResponse> => {
    const res = await api.post<RefundResponse>(
      `/admin/refunds/${encodeURIComponent(id)}/reject`,
      undefined,
      { params: { reason } }
    );
    return res.data;
  },
};
