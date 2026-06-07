import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/test-utils';
import ProductListPage from './ProductListPage';
import { productsApi } from './api';
import { useAuthStore } from '../auth/store';

vi.mock('./api', () => ({
  productsApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    discontinue: vi.fn(),
    stats: vi.fn(),
    get: vi.fn(),
  },
}));

const mockProductsApi = productsApi as unknown as {
  list: ReturnType<typeof vi.fn>;
  create: ReturnType<typeof vi.fn>;
  update: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
  stats: ReturnType<typeof vi.fn>;
};

describe('ProductListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ username: 'admin', role: 'ADMIN', hydrated: true });
    mockProductsApi.list.mockResolvedValue({
      content: [
        {
          id: 'p-1',
          name: '鲜活大虾',
          description: '',
          price: '99.00',
          stock: 100,
          category: '虾蟹',
          imageUrl: '',
          status: 'ACTIVE',
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    });
    mockProductsApi.stats.mockResolvedValue({ total: 1, onSale: 1, outOfStock: 0, byCategory: { 虾蟹: 1 } });
  });

  it('renders the product list and stats', async () => {
    renderWithProviders(<ProductListPage />, { authenticated: true });
    await waitFor(() => {
      expect(screen.getByText('鲜活大虾')).toBeInTheDocument();
    });
    expect(screen.getByText('共 1 款,在售 1,缺货 0')).toBeInTheDocument();
  });

  it('opens the create dialog and shows the form', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProductListPage />, { authenticated: true });
    await waitFor(() => screen.getByText('鲜活大虾'));
    await user.click(screen.getByRole('button', { name: /新增商品/ }));
    expect(screen.getByText('新增商品', { selector: 'h2, h3, [role=heading]' })).toBeInTheDocument();
    // Confirm form is rendered
    expect(screen.getByLabelText('商品名称')).toBeInTheDocument();
  });

  it('opens the delete dialog and calls delete', async () => {
    mockProductsApi.delete.mockResolvedValueOnce(undefined);
    const user = userEvent.setup();
    renderWithProviders(<ProductListPage />, { authenticated: true });
    await waitFor(() => screen.getByText('鲜活大虾'));
    await user.click(screen.getByRole('button', { name: /删除 鲜活大虾/ }));
    await user.click(screen.getByRole('button', { name: '确认删除' }));
    await waitFor(() => {
      expect(mockProductsApi.delete).toHaveBeenCalledWith('p-1');
    });
  });
});
