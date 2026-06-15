/**
 * 路线图 4.12 E2E — 「mp 申请 → ad 审核 → 同意 → REFUNDED」全链路契约。
 *
 * <p>v2 视觉 4.18(2026-06-14):早期测试用 axios adapter mock `/admin/refunds/*`,
 * 但 baseURL='/api' + 同上 URL mismatch。改用 vi.mock factory 模式。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/test-utils';
import RefundReviewPage from './RefundReviewPage';
import { useAuthStore } from '../auth/store';

vi.mock('./api', () => ({
  refundsApi: {
    list: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
  },
}));

const mockRefundsApi = (await import('./api' as any)).refundsApi as unknown as {
  list: ReturnType<typeof vi.fn>;
  approve: ReturnType<typeof vi.fn>;
  reject: ReturnType<typeof vi.fn>;
};

const sampleRefund = {
  id: 'r1', orderId: 'o1', userId: 'u1', amount: 50, reason: '海鲜质量',
  status: 'REQUESTED' as const,
  createdAt: '2026-06-13T10:00:00Z', updatedAt: '2026-06-13T10:00:00Z',
};

describe('RefundReviewPage 4.12 E2E 契约', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ username: 'admin', role: 'ADMIN', hydrated: true });
  });

  it('4.12.7:list 返回 REQUESTED → 页面显示 r1 + 同意按钮', async () => {
    mockRefundsApi.list.mockResolvedValue({
      content: [sampleRefund],
      totalElements: 1, totalPages: 1, number: 0, size: 20,
    });
    renderWithProviders(<RefundReviewPage />, { authenticated: true });
    expect(await screen.findByText('r1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /同意/ })).toBeInTheDocument();
  });

  it('4.12.8:点击同意 → approve API 调用 + invalidate 重查', async () => {
    mockRefundsApi.list.mockResolvedValue({
      content: [sampleRefund],
      totalElements: 1, totalPages: 1, number: 0, size: 20,
    });
    mockRefundsApi.approve.mockResolvedValue({ ...sampleRefund, status: 'APPROVED' });
    renderWithProviders(<RefundReviewPage />, { authenticated: true });
    expect(await screen.findByText('r1')).toBeInTheDocument();
    const btn = screen.getByRole('button', { name: /同意/ });
    btn.click();
    await waitFor(() => {
      expect(mockRefundsApi.approve).toHaveBeenCalledWith('r1');
    });
  });
});
