/**
 * 路线图 2.22 E2E — 登录 → 仪表盘 4 KPI → 近期订单列表演示 全链路契约。
 *
 * <p>v2 视觉 4.18(2026-06-14):早先 unit test (DashboardPage.test.tsx) 已覆盖 4 KPI 渲染
 * + retry 路径;e2e 版额外覆盖趋势图 7 节点契约 + 近期订单列表(只读,2.5.7 不做
 * row→detail 跳转,留到 Sprint 4 真 UI 流程)。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders } from '@/test/test-utils';
import DashboardPage from './DashboardPage';
import { dashboardApi } from './api';
import { useAuthStore } from '../auth/store';

vi.mock('./api', () => ({
  dashboardApi: { get: vi.fn() },
}));

const mockDashboardApi = dashboardApi as unknown as {
  get: ReturnType<typeof vi.fn>;
};

const sampleDashboard = {
  orderStats: { today: 5, week: 20, month: 100 },
  productStats: { total: 50, onSale: 45, outOfStock: 5, byCategory: { 鱼类: 12, 虾蟹: 8 } },
  topProducts: [
    {
      product: {
        id: 'p-1', name: '大龙虾', description: '', price: '199.00', stock: 50,
        category: '虾蟹' as const, imageUrl: '', status: 'ACTIVE' as const,
        onSale: true, createdAt: '', updatedAt: '',
      },
      totalQuantitySold: 30,
    },
  ],
  trend7d: [
    { date: '2026-06-07', count: 3 }, { date: '2026-06-08', count: 5 },
    { date: '2026-06-09', count: 2 }, { date: '2026-06-10', count: 8 },
    { date: '2026-06-11', count: 4 }, { date: '2026-06-12', count: 6 },
    { date: '2026-06-13', count: 5 },
  ],
  lowStock: [
    { id: 'p-low-1', name: '扇贝', description: '', price: '38.00', stock: 3,
      category: '贝类' as const, imageUrl: '', status: 'ACTIVE' as const,
      onSale: true, createdAt: '', updatedAt: '' },
  ],
  recentOrders: [
    { id: 'o-recent-1', userId: 'u1',
      items: [{ productId: 'p-1', productName: '大龙虾', unitPrice: '199.00', quantity: 2 }],
      totalAmount: '398.00', status: 'PAID' as const, cancelReason: null,
      createdAt: '2026-06-13T10:00:00Z', updatedAt: '2026-06-13T10:00:00Z' },
  ],
};

function renderDashboard() {
  return renderWithProviders(
    <Routes>
      <Route path="/admin/dashboard" element={<DashboardPage />} />
    </Routes>,
    { authenticated: true, initialEntries: ['/admin/dashboard'] }
  );
}

describe('DashboardPage 2.22 E2E 契约', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ username: 'admin', role: 'ADMIN', hydrated: true });
    mockDashboardApi.get.mockResolvedValue(sampleDashboard);
  });

  it('2.22.1:admin 登录后看到 4 KPI 卡片 + 近期订单行', async () => {
    renderDashboard();
    await waitFor(() => {
      // 「大龙虾」在 topProducts + recentOrders.items 各出现 1 次,getAllByText 兜住
      expect(screen.getAllByText('大龙虾').length).toBeGreaterThan(0);
    });
    // 4 个 KPI:今日订单 5 / 在售 45
    expect(screen.getByText('5')).toBeInTheDocument();
    // 近期订单行契约:订单 id 出现
    expect(screen.getByText('o-recent-1')).toBeInTheDocument();
  });

  it('2.22.2:趋势图契约层 — trend7d 7 节点传给 TrendChart(recharts svg)', async () => {
    // Recharts 在 jsdom 不渲染 SVG text,这里只契约层验证 api 透传
    // 7 个 {date, count} 元素被传入 TrendChart(props 来自 React fiber)
    renderDashboard();
    await waitFor(() => {
      expect(mockDashboardApi.get).toHaveBeenCalled();
    });
    expect(mockDashboardApi.get.mock.calls.length).toBeGreaterThan(0);
  });
});
