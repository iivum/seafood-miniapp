import { api } from '@/lib/api';
import type { BannerRequest, BannerResponse } from '@/types/api';

/**
 * Banner 管理 API(对齐 productsApi)。
 * 公共读 `/banners`(active);admin 全量 `/banners/all`(含 INACTIVE);
 * 写操作 `/banners`(@PreAuthorize ADMIN)。携带 admin JWT + CSRF 由 lib/api 拦截器统一处理。
 */
export const bannersApi = {
  /** admin 全量列表(含停用),按 sortOrder 升序。 */
  listAll: async (): Promise<BannerResponse[]> => {
    const res = await api.get<BannerResponse[]>('/banners/all');
    return res.data;
  },
  get: async (id: string): Promise<BannerResponse> => {
    const res = await api.get<BannerResponse>(`/banners/${id}`);
    return res.data;
  },
  create: async (body: BannerRequest): Promise<BannerResponse> => {
    const res = await api.post<BannerResponse>('/banners', body);
    return res.data;
  },
  update: async (id: string, body: BannerRequest): Promise<BannerResponse> => {
    const res = await api.put<BannerResponse>(`/banners/${id}`, body);
    return res.data;
  },
  delete: async (id: string): Promise<void> => {
    await api.delete(`/banners/${id}`);
  },
};
