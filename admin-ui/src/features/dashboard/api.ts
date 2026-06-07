import { api } from '@/lib/api';
import type { DashboardResponse } from '@/types/api';

export const dashboardApi = {
  get: async (): Promise<DashboardResponse> => {
    const res = await api.get<DashboardResponse>('/admin/dashboard');
    return res.data;
  },
};
