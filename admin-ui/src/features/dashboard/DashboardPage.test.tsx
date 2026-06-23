import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/test-utils';
import DashboardPage from './DashboardPage';
import { dashboardApi } from './api';
import { useAuthStore } from '../auth/store';

vi.mock('./api', () => ({
  dashboardApi: { get: vi.fn() },
}));

const mockDashboard = dashboardApi as unknown as { get: ReturnType<typeof vi.fn> };

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ username: 'admin', role: 'ADMIN', hydrated: true });
  });

  it('renders the dashboard with stats', async () => {
    mockDashboard.get.mockResolvedValueOnce({
      orderStats: { today: 5, week: 20, month: 100, gmvToday: 1580, avgOrderToday: 316 },
      productStats: { total: 50, onSale: 45, outOfStock: 5, byCategory: { 鱼类: 12, 虾蟹: 8 } },
      topProducts: [
        {
          product: {
            id: 'p-1',
            name: '大龙虾',
            description: '',
            price: '199.00',
            stock: 50,
            category: '虾蟹',
            imageUrl: '',
            status: 'ACTIVE',
            createdAt: '',
            updatedAt: '',
          },
          totalQuantitySold: 30,
        },
      ],
      // 路线图 2.17 / 2.18 / 2.21 新增字段:
      trend7d: [
        { date: '2026-06-07', count: 3 },
        { date: '2026-06-08', count: 5 },
        { date: '2026-06-09', count: 2 },
        { date: '2026-06-10', count: 8 },
        { date: '2026-06-11', count: 4 },
        { date: '2026-06-12', count: 6 },
        { date: '2026-06-13', count: 5 },
      ],
      lowStock: [
        {
          id: 'p-low-1',
          name: '扇贝',
          description: '',
          price: '38.00',
          stock: 3,
          category: '贝类',
          imageUrl: '',
          status: 'ACTIVE',
          createdAt: '',
          updatedAt: '',
        },
      ],
      recentOrders: [
        {
          id: 'o-recent-1',
          userId: 'u1',
          items: [{ productId: 'p-1', productName: '大龙虾', unitPrice: '199.00', quantity: 2 }],
          totalAmount: '398.00',
          status: 'PAID',
          cancelReason: null,
          createdAt: '2026-06-13T10:00:00Z',
          updatedAt: '2026-06-13T10:00:00Z',
        },
      ],
    });
    renderWithProviders(<DashboardPage />, { authenticated: true });
    await waitFor(() => {
      // 2.21 近期订单 + 销量 Top 10 都会渲染「大龙虾」,用 getAllByText 兜住
      expect(screen.getAllByText('大龙虾').length).toBeGreaterThan(0);
    });
    expect(screen.getByText('5')).toBeInTheDocument(); // today orders
    expect(screen.getByText('GMV 今日')).toBeInTheDocument(); // OD ad-02 KPI
    expect(screen.getByText('¥1580.00')).toBeInTheDocument(); // gmvToday formatted
    expect(screen.getByText('¥316.00')).toBeInTheDocument(); // avgOrderToday formatted
    expect(screen.getByText('CONVERSION 转化率')).toBeInTheDocument(); // placeholder card
  });

  it('shows error state with retry button when request fails', async () => {
    mockDashboard.get.mockRejectedValueOnce(new Error('boom'));
    renderWithProviders(<DashboardPage />, { authenticated: true });
    const user = userEvent.setup();
    await waitFor(() => {
      expect(screen.getByText('无法加载仪表盘')).toBeInTheDocument();
    });
    const retry = screen.getByRole('button', { name: /重试/ });
    mockDashboard.get.mockResolvedValueOnce({
      orderStats: { today: 0, week: 0, month: 0, gmvToday: 0, avgOrderToday: 0 },
      productStats: { total: 0, onSale: 0, outOfStock: 0, byCategory: {} },
      topProducts: [],
      trend7d: [],
      lowStock: [],
      recentOrders: [],
    });
    await user.click(retry);
    await waitFor(() => {
      expect(mockDashboard.get).toHaveBeenCalledTimes(2);
    });
  });

  it('LowStock: stock=0 显示已售罄 badge 而非数字', async () => {
    mockDashboard.get.mockResolvedValueOnce({
      orderStats: { today: 0, week: 0, month: 0, gmvToday: 0, avgOrderToday: 0 },
      productStats: { total: 0, onSale: 0, outOfStock: 0, byCategory: {} },
      topProducts: [],
      trend7d: [],
      recentOrders: [],
      lowStock: [
        { id: 'p-sold', name: '蛏子', description: '', price: '25.00', stock: 0,
          category: '贝类', imageUrl: '', status: 'ACTIVE', createdAt: '', updatedAt: '' },
      ],
    });
    renderWithProviders(<DashboardPage />, { authenticated: true });
    await waitFor(() => expect(screen.getByText('蛏子')).toBeInTheDocument());
    expect(screen.getByText('已售罄')).toBeInTheDocument();
    expect(screen.queryByRole('cell', { name: '0' })).not.toBeInTheDocument();
  });

  it('LowStock: 1≤stock<5 显示橙色数字', async () => {
    mockDashboard.get.mockResolvedValueOnce({
      orderStats: { today: 0, week: 0, month: 0, gmvToday: 0, avgOrderToday: 0 },
      productStats: { total: 0, onSale: 0, outOfStock: 0, byCategory: {} },
      topProducts: [],
      trend7d: [],
      recentOrders: [],
      lowStock: [
        { id: 'p-orange', name: '扇贝', description: '', price: '38.00', stock: 3,
          category: '贝类', imageUrl: '', status: 'ACTIVE', createdAt: '', updatedAt: '' },
      ],
    });
    renderWithProviders(<DashboardPage />, { authenticated: true });
    await waitFor(() => expect(screen.getByText('扇贝')).toBeInTheDocument());
    const stockEl = screen.getByText('3');
    expect(stockEl).toHaveClass('text-orange-600');
  });

  it('LowStock: 5≤stock<10 显示黄色数字', async () => {
    mockDashboard.get.mockResolvedValueOnce({
      orderStats: { today: 0, week: 0, month: 0, gmvToday: 0, avgOrderToday: 0 },
      productStats: { total: 0, onSale: 0, outOfStock: 0, byCategory: {} },
      topProducts: [],
      trend7d: [],
      recentOrders: [],
      lowStock: [
        { id: 'p-yellow', name: '鲍鱼', description: '', price: '99.00', stock: 7,
          category: '贝类', imageUrl: '', status: 'ACTIVE', createdAt: '', updatedAt: '' },
      ],
    });
    renderWithProviders(<DashboardPage />, { authenticated: true });
    await waitFor(() => expect(screen.getByText('鲍鱼')).toBeInTheDocument());
    const stockEl = screen.getByText('7');
    expect(stockEl).toHaveClass('text-yellow-600');
  });

  it('LowStock: 空态显示库存健康图标', async () => {
    mockDashboard.get.mockResolvedValueOnce({
      orderStats: { today: 0, week: 0, month: 0, gmvToday: 0, avgOrderToday: 0 },
      productStats: { total: 0, onSale: 0, outOfStock: 0, byCategory: {} },
      topProducts: [],
      trend7d: [],
      recentOrders: [],
      lowStock: [],
    });
    renderWithProviders(<DashboardPage />, { authenticated: true });
    await waitFor(() => expect(screen.getByText('库存健康')).toBeInTheDocument());
    expect(screen.getByText('所有商品库存充足，无需补货')).toBeInTheDocument();
  });

  it('LowStock: 非零库存商品显示「去补货」链接', async () => {
    mockDashboard.get.mockResolvedValueOnce({
      orderStats: { today: 0, week: 0, month: 0, gmvToday: 0, avgOrderToday: 0 },
      productStats: { total: 0, onSale: 0, outOfStock: 0, byCategory: {} },
      topProducts: [],
      trend7d: [],
      recentOrders: [],
      lowStock: [
        { id: 'p-link', name: '海胆', description: '', price: '150.00', stock: 5,
          category: '海鲜', imageUrl: '', status: 'ACTIVE', createdAt: '', updatedAt: '' },
      ],
    });
    renderWithProviders(<DashboardPage />, { authenticated: true });
    await waitFor(() => expect(screen.getByText('海胆')).toBeInTheDocument());
    const link = screen.getByRole('link', { name: '去补货' });
    expect(link).toHaveAttribute('href', '/admin/products');
  });
});
