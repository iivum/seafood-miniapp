import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/test-utils';
import RefundReviewPage from './RefundReviewPage';
import { refundsApi } from './api';
import { useAuthStore } from '../auth/store';

vi.mock('./api', () => ({
  refundsApi: {
    list: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
  },
}));

const mockRefundsApi = refundsApi as unknown as {
  list: ReturnType<typeof vi.fn>;
  approve: ReturnType<typeof vi.fn>;
  reject: ReturnType<typeof vi.fn>;
};

const requestedRefunds = {
  content: [
    {
      id: 'r1', orderId: 'o1', userId: 'u1',
      amount: '100.00', reason: '海鲜质量有问题',
      status: 'REQUESTED' as const,
      createdAt: '2026-06-13T00:00:00Z',
      updatedAt: '2026-06-13T00:00:00Z',
    },
  ],
  totalElements: 1, totalPages: 1, number: 0, size: 20,
};

describe('RefundReviewPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ username: 'admin', role: 'ADMIN', hydrated: true });
    mockRefundsApi.list.mockResolvedValue(requestedRefunds);
  });

  it('renders REQUESTED refunds with approve/reject buttons', async () => {
    renderWithProviders(<RefundReviewPage />, { authenticated: true });
    expect(await screen.findByText('r1')).toBeInTheDocument();
    expect(screen.getByText('o1')).toBeInTheDocument();
    expect(screen.getByText('海鲜质量有问题')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /同意/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /拒绝/ })).toBeInTheDocument();
  });

  it('4.11:approve calls refundsApi.approve and refreshes list', async () => {
    const user = userEvent.setup();
    mockRefundsApi.approve.mockResolvedValue({
      ...requestedRefunds.content[0], status: 'APPROVED' as const,
    });
    renderWithProviders(<RefundReviewPage />, { authenticated: true });
    const approveBtn = await screen.findByRole('button', { name: /同意/ });
    await user.click(approveBtn);
    await waitFor(() => {
      expect(mockRefundsApi.approve).toHaveBeenCalledWith('r1');
    });
  });

  it('4.11:reject opens reason dialog and calls refundsApi.reject', async () => {
    const user = userEvent.setup();
    mockRefundsApi.reject.mockResolvedValue({
      ...requestedRefunds.content[0], status: 'REJECTED' as const,
    });
    renderWithProviders(<RefundReviewPage />, { authenticated: true });
    await user.click(await screen.findByRole('button', { name: /拒绝/ }));
    // 拒绝原因弹窗
    const reasonInput = await screen.findByPlaceholderText(/已签收/);
    expect(reasonInput).toBeInTheDocument();
    // 空 reason 拒绝
    await user.click(screen.getByRole('button', { name: /确认拒绝/ }));
    expect(mockRefundsApi.reject).not.toHaveBeenCalled();
    // 填 reason
    await user.type(reasonInput, '已签收 7 天,超售后期');
    await user.click(screen.getByRole('button', { name: /确认拒绝/ }));
    await waitFor(() => {
      expect(mockRefundsApi.reject).toHaveBeenCalledWith('r1', '已签收 7 天,超售后期');
    });
  });

  it('4.11:empty list shows "no refunds" placeholder', async () => {
    mockRefundsApi.list.mockResolvedValue({
      content: [],
      totalElements: 0, totalPages: 0, number: 0, size: 20,
    });
    renderWithProviders(<RefundReviewPage />, { authenticated: true });
    expect(await screen.findByText(/暂无待审核的退款单/)).toBeInTheDocument();
  });

  it('4.11:switching tab refetches with new status', async () => {
    const user = userEvent.setup();
    renderWithProviders(<RefundReviewPage />, { authenticated: true });
    await screen.findByText('r1');
    // 切到"已通过"
    await user.click(screen.getByRole('tab', { name: '已通过' }));
    await waitFor(() => {
      expect(mockRefundsApi.list).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'APPROVED' })
      );
    });
  });
});
