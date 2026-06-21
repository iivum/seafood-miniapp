import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/test-utils';
import BannerListPage from './BannerListPage';
import { bannersApi } from './api';
import { useAuthStore } from '../auth/store';
import type { BannerResponse } from '@/types/api';

vi.mock('./api', () => ({
  bannersApi: {
    listAll: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

const mockApi = bannersApi as unknown as {
  listAll: ReturnType<typeof vi.fn>;
  create: ReturnType<typeof vi.fn>;
  update: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
};

const SAMPLE: BannerResponse = {
  id: 'b-1',
  tone: 'ACCENT',
  emoji: '🦞',
  title: '波龙季 返场',
  subtitle: '鲜活到岸 · 满 1 只减 30',
  targetProductId: null,
  sortOrder: 0,
  status: 'ACTIVE',
  createdAt: '2026-06-20T00:00:00Z',
  updatedAt: '2026-06-20T00:00:00Z',
};

describe('BannerListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ username: 'admin', role: 'ADMIN', hydrated: true });
    mockApi.listAll.mockResolvedValue([SAMPLE]);
  });

  it('renders the banner list', async () => {
    renderWithProviders(<BannerListPage />, { authenticated: true });
    await waitFor(() => {
      expect(screen.getByText('波龙季 返场')).toBeInTheDocument();
    });
    expect(screen.getByText('启用')).toBeInTheDocument();
  });

  it('opens the create dialog with an empty form', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BannerListPage />, { authenticated: true });
    await waitFor(() => screen.getByText('波龙季 返场'));
    await user.click(screen.getByRole('button', { name: /新建 Banner/ }));
    expect(screen.getByText('新建 Banner', { selector: 'h2, h3, [role=heading]' })).toBeInTheDocument();
    expect(screen.getByLabelText('标题')).toBeInTheDocument();
  });

  it('creates a banner via the form', async () => {
    mockApi.create.mockResolvedValueOnce({ ...SAMPLE, id: 'b-new' });
    const user = userEvent.setup();
    renderWithProviders(<BannerListPage />, { authenticated: true });
    await waitFor(() => screen.getByText('波龙季 返场'));
    await user.click(screen.getByRole('button', { name: /新建 Banner/ }));
    await user.type(screen.getByLabelText('Emoji'), '🦀');
    await user.type(screen.getByLabelText('标题'), '大闸蟹 旺季');
    await user.type(screen.getByLabelText('副标题'), '公 4 两 · 整 8 只装');
    await user.click(screen.getByRole('button', { name: '保存' }));
    await waitFor(() => {
      expect(mockApi.create).toHaveBeenCalledWith(
        expect.objectContaining({
          title: '大闸蟹 旺季',
          subtitle: '公 4 两 · 整 8 只装',
          active: true,
        }),
      );
    });
  });

  it('deletes a banner', async () => {
    mockApi.delete.mockResolvedValueOnce(undefined);
    const user = userEvent.setup();
    renderWithProviders(<BannerListPage />, { authenticated: true });
    await waitFor(() => screen.getByText('波龙季 返场'));
    await user.click(screen.getByRole('button', { name: '删除' }));
    await waitFor(() => {
      expect(mockApi.delete).toHaveBeenCalledWith('b-1');
    });
  });
});
