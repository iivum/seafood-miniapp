import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/test-utils';
import { ProductForm } from './ProductForm';

describe('ProductForm validation', () => {
  it('rejects empty submission and shows errors', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ProductForm submitLabel="保存" submitting={false} onSubmit={onSubmit} onCancel={vi.fn()} />,
    );
    await user.click(screen.getByRole('button', { name: '保存' }));
    await waitFor(() => {
      expect(onSubmit).not.toHaveBeenCalled();
    });
    expect(screen.getByText('请输入商品名称')).toBeInTheDocument();
  });

  it('submits valid values', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderWithProviders(
      <ProductForm
        submitLabel="保存"
        submitting={false}
        onSubmit={onSubmit}
        onCancel={vi.fn()}
        defaultValues={{ name: '活虾', description: '新鲜', price: 99, stock: 10, category: '虾蟹', imageUrl: '' }}
      />,
    );
    await user.click(screen.getByRole('button', { name: '保存' }));
    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({ name: '活虾', category: '虾蟹', price: 99, stock: 10 }),
      );
    });
  });

  it('rejects zero price', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ProductForm submitLabel="保存" submitting={false} onSubmit={onSubmit} onCancel={vi.fn()} />,
    );
    await user.type(screen.getByLabelText('商品名称'), '商品');
    await user.type(screen.getByLabelText(/价格/), '0');
    await user.click(screen.getByRole('button', { name: '保存' }));
    await waitFor(() => {
      expect(onSubmit).not.toHaveBeenCalled();
    });
    expect(screen.getByText('价格必须大于 0')).toBeInTheDocument();
  });
});
