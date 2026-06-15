import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders } from '@/test/test-utils';
import OrderDetailPage from './OrderDetailPage';
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
  detail: ReturnType<typeof vi.fn>;
  ship: ReturnType<typeof vi.fn>;
};

const paidDetail = {
  order: {
    id: 'o1', userId: 'u1', totalAmount: '50.00', status: 'PAID' as const,
    items: [{ productId: 'p1', productName: 'X', unitPrice: '50', quantity: 1 }],
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

const shippedDetail = {
  ...paidDetail,
  order: {
    ...paidDetail.order, status: 'SHIPPED' as const,
    tracking: { carrier: '顺丰', trackingNumber: 'SF123', events: [
      { at: '2026-06-13T10:00:00Z', status: 'SHIPPED', location: '上海', description: '已发货' },
    ] },
  },
};

/**
 * v2 视觉 4.18 — OrderDetailPage 契约测试。
 *
 * 关键修复(2026-06-14):useParams<{id: string}>() 必须在带 `:id` param
 * 的 Route 上下文才能拿到。早期测试用裸 MemoryRouter + 顶层
 * initialEntries,useParams 返 {},query enabled=false,组件卡 Skeleton。
 * 修复:用 `<Routes><Route path="orders/:id" element={...} /></Routes>` 包装,
 * 让 useParams 拿到 id。
 */
function renderDetailPage(options: { initialEntries: string[] } = { initialEntries: ['/admin/orders/o1'] }) {
  return renderWithProviders(
    <Routes>
      <Route path="admin/orders/:id" element={<OrderDetailPage />} />
    </Routes>,
    {
      authenticated: true,
      ...options,
    }
  );
}

describe('OrderDetailPage 4.18 — 3 列布局契约', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ username: 'admin', role: 'ADMIN', hydrated: true });
  });

  it('renders 返回订单列表 link on loaded PAID order', async () => {
    mockOrdersApi.detail.mockResolvedValue(paidDetail);
    renderDetailPage();
    // 返回列表是 <Link>→ <a> (role: link),不是 button
    expect(await screen.findByRole('link', { name: /返回.*列表/ })).toBeInTheDocument();
  });

  it('PAID order: shows 发货 button, calls ship', async () => {
    const user = userEvent.setup();
    mockOrdersApi.ship.mockResolvedValue({ ...paidDetail.order, status: 'SHIPPED' });
    mockOrdersApi.detail.mockResolvedValue(paidDetail);
    renderDetailPage();
    const shipBtn = await screen.findByRole('button', { name: /发货/ });
    await user.click(shipBtn);
    await waitFor(() => {
      expect(mockOrdersApi.ship).toHaveBeenCalledWith('o1');
    });
  });

  it('SHIPPED order: shows 已发货 timeline, hides PAID-only 发货 button', async () => {
    mockOrdersApi.detail.mockResolvedValue(shippedDetail);
    renderDetailPage();
    // 多处出现"已发货"(badge + timeline 节点),用 findAllByText
    expect((await screen.findAllByText('已发货')).length).toBeGreaterThan(0);
    // SHIPPED 已发过,PAID-only 发货按钮不显示
    expect(screen.queryByRole('button', { name: /^\s*发货\s*$/ })).not.toBeInTheDocument();
  });

  it('打印拣货单 button opens new window to /api/admin/orders/{id}/print-picklist', async () => {
    mockOrdersApi.detail.mockResolvedValue(paidDetail);
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
    renderDetailPage();
    const printBtn = await screen.findByRole('button', { name: /打印拣货单/ });
    printBtn.click();
    expect(openSpy).toHaveBeenCalledWith('/api/admin/orders/o1/print-picklist', '_blank');
    openSpy.mockRestore();
  });

  it('REFUNDING order: shows 查看退款 button (navigates to /admin/refunds)', async () => {
    const refundingDetail = {
      ...paidDetail,
      order: { ...paidDetail.order, status: 'REFUNDING' as const },
    };
    mockOrdersApi.detail.mockResolvedValue(refundingDetail);
    renderDetailPage();
    // 查看退款是 Button(用 navigate onClick),不是 Link
    expect(await screen.findByRole('button', { name: /查看退款/ })).toBeInTheDocument();
  });
});
