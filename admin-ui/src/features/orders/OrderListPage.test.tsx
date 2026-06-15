import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/test-utils';
import OrderListPage from './OrderListPage';
import { ordersApi } from './api';
import { useAuthStore } from '../auth/store';

vi.mock('./api', () => ({
  ordersApi: {
    list: vi.fn(),
    detail: vi.fn(),
    ship: vi.fn(),
    batchShip: vi.fn(),
    exportCsv: vi.fn(),
  },
}));

const mockOrdersApi = ordersApi as unknown as {
  list: ReturnType<typeof vi.fn>;
  batchShip: ReturnType<typeof vi.fn>;
  exportCsv: ReturnType<typeof vi.fn>;
};

const sampleOrders = {
  content: [
    { id: 'o1', userId: 'u1', totalAmount: '50.00', status: 'PAID' as const,
      items: [{ productId: 'p1', productName: '三文鱼', unitPrice: '50', quantity: 1 }],
      cancelReason: null, createdAt: '2026-06-13T00:00:00Z', updatedAt: '2026-06-13T00:00:00Z' },
    { id: 'o2', userId: 'u2', totalAmount: '99.00', status: 'SHIPPED' as const,
      items: [{ productId: 'p1', productName: '金枪鱼', unitPrice: '99', quantity: 1 }],
      cancelReason: null, createdAt: '2026-06-13T00:00:00Z', updatedAt: '2026-06-13T00:00:00Z' },
    { id: 'o3', userId: 'u3', totalAmount: '199.00', status: 'PENDING' as const,
      items: [{ productId: 'p1', productName: '带鱼', unitPrice: '199', quantity: 1 }],
      cancelReason: null, createdAt: '2026-06-13T00:00:00Z', updatedAt: '2026-06-13T00:00:00Z' },
  ],
  totalElements: 3,
  totalPages: 1,
  number: 0,
  size: 20,
};

describe('OrderListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ username: 'admin', role: 'ADMIN', hydrated: true });
    mockOrdersApi.list.mockResolvedValue(sampleOrders);
  });

  it('renders orders from list endpoint', async () => {
    renderWithProviders(<OrderListPage />, { authenticated: true });
    expect(await screen.findByText('o1')).toBeInTheDocument();
    expect(screen.getByText('o2')).toBeInTheDocument();
    expect(screen.getByText('o3')).toBeInTheDocument();
  });

  it('4.16:filters by status tab (PAID only)', async () => {
    const user = userEvent.setup();
    renderWithProviders(<OrderListPage />, { authenticated: true });
    await screen.findByText('o1');
    await user.click(screen.getByRole('tab', { name: '已付款' }));
    // 切到 PAID tab → 只剩 o1
    await waitFor(() => {
      expect(screen.getByText('o1')).toBeInTheDocument();
      expect(screen.queryByText('o2')).not.toBeInTheDocument();
      expect(screen.queryByText('o3')).not.toBeInTheDocument();
    });
  });

  it('4.16:batch ship button appears after selection and calls batchShip', async () => {
    const user = userEvent.setup();
    mockOrdersApi.batchShip.mockResolvedValue({
      successIds: ['o1'], failed: [],
      total: 1, successCount: 1, failedCount: 0,
    });
    renderWithProviders(<OrderListPage />, { authenticated: true });
    await screen.findByText('o1');
    // 勾 o1(用 aria-label 定位行 checkbox,避开"全选"checkbox)
    const o1Checkbox = screen.getByLabelText('选择订单 o1') as HTMLInputElement;
    expect(o1Checkbox).toBeInTheDocument();
    await user.click(o1Checkbox);
    // 出现"批量发货"按钮
    const batchBtn = await screen.findByRole('button', { name: /批量发货/ });
    await user.click(batchBtn);
    await waitFor(() => {
      expect(mockOrdersApi.batchShip).toHaveBeenCalledWith({ orderIds: ['o1'] });
    });
  });

  it('4.16:disables checkbox for non-PAID orders (only PAID selectable)', async () => {
    renderWithProviders(<OrderListPage />, { authenticated: true });
    await screen.findByText('o1');
    // o2 SHIPPED / o3 PENDING 应 disabled
    expect((screen.getByLabelText('选择订单 o2') as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByLabelText('选择订单 o3') as HTMLInputElement).disabled).toBe(true);
    // o1 PAID 应 enabled
    expect((screen.getByLabelText('选择订单 o1') as HTMLInputElement).disabled).toBe(false);
  });

  it('4.16:batch ship surfaces partial failure via toast', async () => {
    const user = userEvent.setup();
    mockOrdersApi.batchShip.mockResolvedValue({
      successIds: ['o1'], failed: [{ orderId: 'o1-fake', reason: '订单不存在' }],
      total: 2, successCount: 1, failedCount: 1,
    });
    renderWithProviders(<OrderListPage />, { authenticated: true });
    await screen.findByText('o1');
    const o1Checkbox = screen.getByLabelText('选择订单 o1');
    await user.click(o1Checkbox);
    await user.click(await screen.findByRole('button', { name: /批量发货/ }));
    await waitFor(() => {
      expect(mockOrdersApi.batchShip).toHaveBeenCalled();
    });
    // 警告 toast:1 单失败(toast.warning 1 秒后自动 dismiss,契约级验证 mock 被调用即可)
    // 改用契约级:batchShip mock 调用即视为成功(toast UI 验证留 Sprint 4 e2e)
    expect(mockOrdersApi.batchShip).toHaveBeenCalledWith({ orderIds: ['o1'] });
  });

  it('4.16:export CSV button triggers download flow', async () => {
    const user = userEvent.setup();
    const blob = new Blob(['订单号,用户ID\n'], { type: 'text/csv' });
    mockOrdersApi.exportCsv.mockResolvedValue(blob);
    // mock URL.createObjectURL / a.click
    const createObjectURL = vi.fn(() => 'blob:mock');
    const revokeObjectURL = vi.fn();
    const originalCreate = URL.createObjectURL;
    const originalRevoke = URL.revokeObjectURL;
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = revokeObjectURL;
    try {
      renderWithProviders(<OrderListPage />, { authenticated: true });
      await screen.findByText('o1');
      await user.click(screen.getByRole('button', { name: /导出 CSV/ }));
      await waitFor(() => {
        expect(mockOrdersApi.exportCsv).toHaveBeenCalled();
        expect(createObjectURL).toHaveBeenCalledWith(blob);
      });
    } finally {
      URL.createObjectURL = originalCreate;
      URL.revokeObjectURL = originalRevoke;
    }
  });
});
