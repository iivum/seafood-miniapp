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
      orderStats: { today: 5, week: 20, month: 100 },
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
    });
    renderWithProviders(<DashboardPage />, { authenticated: true });
    await waitFor(() => {
      expect(screen.getByText('大龙虾')).toBeInTheDocument();
    });
    expect(screen.getByText('5')).toBeInTheDocument(); // today orders
    expect(screen.getByText('45')).toBeInTheDocument(); // onSale
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
      orderStats: { today: 0, week: 0, month: 0 },
      productStats: { total: 0, onSale: 0, outOfStock: 0, byCategory: {} },
      topProducts: [],
    });
    await user.click(retry);
    await waitFor(() => {
      expect(mockDashboard.get).toHaveBeenCalledTimes(2);
    });
  });
});
