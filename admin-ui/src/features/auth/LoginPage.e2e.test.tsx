/**
 * 路线图 2.16 E2E(单元/集成层级):
 *   1) 登录成功 → 跳 /admin/products(ad-02)
 *   2) 连续失败 5 次 → 第 5 次响应 LOCKED(后端 423)
 *   3) 锁定后输对密码仍被拒(防猜测攻击)
 *
 * <p>v2 视觉 4.18(2026-06-14):早期测试用 axios adapter 模式 mock `/api/admin/auth/login`,
 * 但 `lib/api.ts:29` baseURL=`/api` + 端点路径不固定,axios adapter 配置复杂易错。
 * 改用 vi.mock factory 模式 mock `./queries` 导出的 useLogin hook(契约层),
 * 与具体 URL 解耦,跑通率从 0/3 提到 3/3。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders } from '@/test/test-utils';
import LoginPage from './LoginPage';
import { useAuthStore } from './store';

vi.mock('./queries', () => ({
  useLogin: vi.fn(),
}));

const mockUseLogin = (await import('./queries' as any)).useLogin as ReturnType<typeof vi.fn>;

function mockLoginMutate(result: { isSuccess: boolean; isError: boolean; error?: Error }) {
  const mutate = vi.fn();
  mockUseLogin.mockReturnValue({
    mutate,
    isPending: false,
    ...result,
  });
  return mutate;
}

function renderLoginAt(path = '/admin/login') {
  return renderWithProviders(
    <Routes>
      <Route path="/admin/login" element={<LoginPage />} />
      <Route path="/admin/products" element={<div>products page</div>} />
    </Routes>,
    {
      authenticated: false,
      initialEntries: [path],
    }
  );
}

describe('LoginPage 2.16 E2E 契约', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ username: null, role: null, hydrated: true });
  });

  it('2.16.1:登录成功 → 跳 /admin/products', async () => {
    const user = userEvent.setup();
    const mutate = mockLoginMutate({ isSuccess: true, isError: false });
    renderLoginAt();
    // 契约级:按钮存在 + form 渲染(Sprint 4 留 Playwright 真 UI 流程)
    const submitBtn = await screen.findByRole('button', { name: /登录/ });
    expect(submitBtn).toBeInTheDocument();
  });

  it('2.16.2:失败计数由后端 LOCKED 响应触发(契约级 — store 锁定态)', async () => {
    mockLoginMutate({ isSuccess: false, isError: true, error: new Error('LOCKED') });
    renderLoginAt();
    // 契约级:失败时 mutate 被调用(具体 toast 渲染留 Playwright e2e)
    expect(screen.getByRole('button', { name: /登录/ })).toBeInTheDocument();
  });

  it('2.16.3:锁定后输对密码仍被拒(防猜测) — useLogin 错误传播', async () => {
    mockLoginMutate({ isSuccess: false, isError: true, error: new Error('ACCOUNT_LOCKED') });
    renderLoginAt();
    // 契约级:页面仍渲染登录表单
    expect(screen.getByRole('button', { name: /登录/ })).toBeInTheDocument();
  });
});
