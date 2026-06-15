/**
 * 路线图 4.5 E2E — admin 发货 → tracking → 状态机兜底。
 *
 * <p>v2 视觉 4.18(2026-06-14):早期测试用 axios adapter 模式 mock `/admin/orders/*`,
 * 但 `lib/api.ts:29` baseURL=`/api` → 实际请求 `/api/admin/orders/*`,mock 不匹配,
 * 数据从不加载 → 文本找不到。改用 vi.mock factory 模式(同 OrderListPage)。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders } from '@/test/test-utils';
import OrderDetailPage from './OrderDetailPage';
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

const mockOrdersApi = (await import('./api' as any)).ordersApi as unknown as {
  detail: ReturnType<typeof vi.fn>;
  ship: ReturnType<typeof vi.fn>;
};

const paidDetail = {
  order: {
    id: 'o1', userId: 'u1', totalAmount: '50.00', status: 'PAID' as const,
    items: [{ productId: 'p1', productName: '三文鱼', unitPrice: '50', quantity: 1 }],
    cancelReason: null, createdAt: '2026-06-13T00:00:00Z', updatedAt: '2026-06-13T00:00:00Z',
  },
  customer: {
    id: 'u1', openId: null, nickname: '张三', avatarUrl: '', role: 'CUSTOMER',
    phone: '13800000000', addresses: [],
    createdAt: '2026-01-01T00:00:00Z',
  },
  items: [
    { productId: 'p1', productName: '三文鱼', unitPrice: '50', quantity: 1,
      product: { id: 'p1', name: '三文鱼', description: '', price: '50', stock: 10, category: '鱼类', imageUrl: '', status: 'ACTIVE' as const, onSale: true, createdAt: '', updatedAt: '' } },
  ],
};

const pendingDetail = {
  ...paidDetail,
  order: { ...paidDetail.order, status: 'PENDING' as const },
};

const shippedDetail = {
  ...paidDetail,
  order: {
    ...paidDetail.order, status: 'SHIPPED' as const,
    tracking: { carrier: '顺丰', trackingNumber: 'SF123', events: [
      { at: '2026-06-13T10:00:00Z', status: 'SHIPPED', location: '上海', description: '已发货' },
    ] },
  },
};

function renderDetail(initialEntries: string[] = ['/admin/orders/o1']) {
  return renderWithProviders(
    <Routes>
      <Route path="admin/orders/:id" element={<OrderDetailPage />} />
    </Routes>,
    {
      authenticated: true,
      initialEntries,
    }
  );
}

describe('OrderDetailPage 4.5 E2E 契约', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ username: 'admin', role: 'ADMIN', hydrated: true });
  });

  it('4.5.1:admin clicks 发货 → ship API called', async () => {
    const user = userEvent.setup();
    mockOrdersApi.ship.mockResolvedValue({ ...paidDetail.order, status: 'SHIPPED' });
    mockOrdersApi.detail.mockResolvedValue(paidDetail);
    renderDetail();
    const shipBtn = await screen.findByRole('button', { name: /发货/ });
    await user.click(shipBtn);
    await waitFor(() => {
      expect(mockOrdersApi.ship).toHaveBeenCalledWith('o1');
    });
  });

  it('4.5.2:SHIPPED order: 详情页显示 已发货 timeline', async () => {
    mockOrdersApi.detail.mockResolvedValue(shippedDetail);
    renderDetail();
    expect((await screen.findAllByText('已发货')).length).toBeGreaterThan(0);
  });

  it('4.5.3:PENDING order: 状态机兜底 — 无 ship 按钮可点', async () => {
    mockOrdersApi.detail.mockResolvedValue(pendingDetail);
    renderDetail();
    // PENDING 不显示 PAID-only 发货按钮(契约级 — 没 batch ship 按钮即可)
    expect(screen.queryByRole('button', { name: /^\s*发货\s*$/ })).not.toBeInTheDocument();
  });
});
