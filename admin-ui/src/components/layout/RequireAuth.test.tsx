import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render as rtlRender } from '@testing-library/react';
import { renderWithProviders } from '@/test/test-utils';
import { LoginPage } from '@/features/auth/LoginPage';
import { useAuthStore } from '@/features/auth/store';
import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAnonymous, RequireAuth } from './RequireAuth';
import { useEffect } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

function GuardedHarness() {
  return (
    <Routes>
      <Route element={<RequireAuth />}>
        <Route path="/admin/dashboard" element={<div>仪表盘占位</div>} />
        <Route path="/admin/products" element={<div>商品占位</div>} />
      </Route>
      <Route element={<RequireAnonymous />}>
        <Route path="/admin/login" element={<LoginPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/admin/dashboard" replace />} />
    </Routes>
  );
}

function HydrationProbe() {
  const hydrated = useAuthStore((s) => s.hydrated);
  useEffect(() => {}, [hydrated]);
  return <div data-testid="hydrated">{String(hydrated)}</div>;
}

function withNoClient() {
  const client = new QueryClient();
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
}

describe('route protection: redirect branches', () => {
  beforeEach(() => {
    localStorage.clear();
    document.cookie = 'admin_refresh_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  it('redirects anonymous user from /admin/dashboard to login form', () => {
    useAuthStore.setState({ username: null, role: null, hydrated: true });
    const { container } = renderWithProviders(<GuardedHarness />, {
      route: '/admin/dashboard',
      authenticated: false,
    });
    expect(container.textContent).toContain('海鲜商城管理后台');
  });

  it('redirects anonymous user from /admin/products to login form', () => {
    useAuthStore.setState({ username: null, role: null, hydrated: true });
    const { container } = renderWithProviders(<GuardedHarness />, {
      route: '/admin/products',
      authenticated: false,
    });
    expect(container.textContent).toContain('海鲜商城管理后台');
  });

  it('redirects already-authed user away from /admin/login', () => {
    useAuthStore.setState({ username: 'admin', role: 'ADMIN', hydrated: true });
    const { container } = renderWithProviders(<GuardedHarness />, {
      route: '/admin/login',
      authenticated: true,
    });
    expect(container.textContent).not.toContain('请使用管理员账号登录');
  });

  it('HydrationProbe reflects the hydrated flag', () => {
    useAuthStore.setState({ hydrated: true });
    const { getByTestId } = renderWithProviders(<HydrationProbe />, { authenticated: false });
    expect(getByTestId('hydrated').textContent).toBe('true');
  });
});

describe('route protection: loading branch', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('RequireAuth shows the loading placeholder when not yet hydrated', () => {
    useAuthStore.setState({ username: 'admin', role: 'ADMIN', hydrated: false });
    const { container } = rtlRender(
      <MemoryRouter initialEntries={['/admin/dashboard']}>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/admin/dashboard" element={<div>应当不可见</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
      { wrapper: withNoClient() },
    );
    expect(container.textContent).toContain('正在加载…');
  });

  it('RequireAnonymous shows the loading placeholder when not yet hydrated', () => {
    useAuthStore.setState({ username: null, role: null, hydrated: false });
    const { container } = rtlRender(
      <MemoryRouter initialEntries={['/admin/login']}>
        <Routes>
          <Route element={<RequireAnonymous />}>
            <Route path="/admin/login" element={<div>应当不可见</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
      { wrapper: withNoClient() },
    );
    expect(container.textContent).toContain('正在加载…');
  });
});

describe('mock smoke test', () => {
  it('vi works', () => {
    expect(vi).toBeDefined();
  });
});
