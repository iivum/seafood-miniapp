import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/test-utils';
import ProductListPage from './ProductListPage';
import { productsApi } from './api';

vi.mock('./api', () => ({
  productsApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    discontinue: vi.fn(),
    stats: vi.fn(),
    get: vi.fn(),
    duplicate: vi.fn(),
    batchStatus: vi.fn(),
  },
}));

const mockApi = productsApi as unknown as {
  list: ReturnType<typeof vi.fn>;
  duplicate: ReturnType<typeof vi.fn>;
  batchStatus: ReturnType<typeof vi.fn>;
  stats: ReturnType<typeof vi.fn>;
};

const sampleProducts = {
  content: [
    { id: 'p1', name: '三文鱼', description: '新鲜', price: '99', stock: 10, category: '鱼类',
      imageUrl: '', status: 'ACTIVE', createdAt: '', updatedAt: '' },
    { id: 'p2', name: '金枪鱼', description: '', price: '199', stock: 5, category: '鱼类',
      imageUrl: '', status: 'OUT_OF_STOCK', createdAt: '', updatedAt: '' },
    { id: 'p3', name: '龙虾', description: '', price: '299', stock: 3, category: '虾蟹',
      imageUrl: '', status: 'ACTIVE', createdAt: '', updatedAt: '' },
  ],
  page: 0, totalPages: 1, totalProducts: 3, hasNext: false, hasPrev: false,
};

const sampleStats = { total: 3, onSale: 2, outOfStock: 1, discontinued: 0 };

/**
 * 路线图 3.4 E2E — ad-03 5 段契约:
 * 1) 切分类 tab 触发 list({category:'鱼类'})
 * 2) 勾 2 行 + 批量上架 → batchStatus
 * 3) 导出 CSV → window.open(/admin/products/export)
 * 4) 复制行 → duplicate(3.1 复用)
 * 5) 3 状态 tab 过滤 → list({status:'ACTIVE'})
 */
describe('3.4 E2E: ad-03 筛选 → 批量上架 → 导出 → duplicate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApi.list.mockResolvedValue(sampleProducts);
    mockApi.stats.mockResolvedValue(sampleStats);
    mockApi.batchStatus.mockResolvedValue({
      total: 2, successCount: 2, failedCount: 0, successIds: ['p1', 'p2'], failed: [],
    });
    mockApi.duplicate.mockResolvedValue({
      id: 'p1-copy', name: '三文鱼 (副本)', description: '', price: '99', stock: 0,
      category: '鱼类', imageUrl: '', status: 'ACTIVE', createdAt: '', updatedAt: '',
    });
    // 拦截 window.open
    window.open = vi.fn();
  });

  it('3.4.1:切分类 tab 鱼类 → list 传 {category:"鱼类"}', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProductListPage />, { authenticated: true });
    await screen.findByText('三文鱼');

    await user.click(screen.getByRole('tab', { name: '鱼类' }));
    await waitFor(() => {
      expect(mockApi.list).toHaveBeenCalledWith(
        expect.objectContaining({ category: '鱼类' }),
      );
    });
  });

  it('3.4.2:勾 2 行 + 批量上架 → batchStatus {ids:["p1","p2"], status:"ACTIVE"}', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProductListPage />, { authenticated: true });
    await screen.findByText('三文鱼');

    await user.click(screen.getByLabelText('选择 三文鱼'));
    await user.click(screen.getByLabelText('选择 金枪鱼'));
    await user.click(screen.getByRole('button', { name: /批量上架/ }));

    await waitFor(() => {
      expect(mockApi.batchStatus).toHaveBeenCalledWith({
        ids: ['p1', 'p2'],
        status: 'ACTIVE',
      });
    });
  });

  it('3.4.3:导出 CSV → window.open(/api/admin/products/export)', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProductListPage />, { authenticated: true });
    await screen.findByText('三文鱼');

    await user.click(screen.getByRole('button', { name: /导出 CSV/ }));
    expect(window.open).toHaveBeenCalledWith('/api/admin/products/export', '_blank');
  });

  it('3.4.4:复制行 → duplicate(p1)', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProductListPage />, { authenticated: true });
    await screen.findByText('三文鱼');

    await user.click(screen.getByLabelText('复制 三文鱼'));
    await waitFor(() => {
      expect(mockApi.duplicate).toHaveBeenCalledWith('p1');
    });
  });

  it('3.4.5:3 状态 tab 切"在售" → list 传 {status:"ACTIVE"}', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProductListPage />, { authenticated: true });
    await screen.findByText('三文鱼');

    await user.click(screen.getByRole('tab', { name: '在售' }));
    await waitFor(() => {
      expect(mockApi.list).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'ACTIVE' }),
      );
    });
  });
});
