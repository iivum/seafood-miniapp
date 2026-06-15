import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/test-utils';
import LoginPage from './LoginPage';

// 隔离 storage mock:每测试都重置
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
    clear: () => { store = {}; },
  };
})();
Object.defineProperty(globalThis, 'localStorage', { value: localStorageMock, writable: true });

// 隔离 sessionStorage 同样
const sessionStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
    clear: () => { store = {}; },
  };
})();
Object.defineProperty(globalThis, 'sessionStorage', { value: sessionStorageMock, writable: true });

const mockLogin = vi.fn();
vi.mock('./queries', () => ({
  useLogin: () => ({
    mutateAsync: mockLogin,
    isPending: false,
    mutate: vi.fn(),
  }),
}));

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorageMock.clear();
    mockLogin.mockResolvedValue({ accessToken: 't', refreshToken: 'r' });
  });

  afterEach(() => {
    localStorageMock.clear();
  });

  it('renders username + password + remember me + login button', () => {
    renderWithProviders(<LoginPage />);
    expect(screen.getByLabelText('用户名')).toBeInTheDocument();
    expect(screen.getByLabelText('密码')).toBeInTheDocument();
    expect(screen.getByLabelText(/记住我/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '登录' })).toBeInTheDocument();
  });

  it('shows validation errors when fields empty', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginPage />);
    await user.click(screen.getByRole('button', { name: '登录' }));
    expect(await screen.findByText('请输入用户名')).toBeInTheDocument();
    expect(await screen.findByText('请输入密码')).toBeInTheDocument();
  });

  it('2.14:remembers username on successful login when checkbox is checked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginPage />);
    await user.type(screen.getByLabelText('用户名'), 'admin');
    await user.type(screen.getByLabelText('密码'), 'secret');
    // remember checkbox 默认为未勾(因 storage 是空的)— 显式勾上
    await user.click(screen.getByLabelText(/记住我/));
    await user.click(screen.getByRole('button', { name: '登录' }));
    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith({ username: 'admin', password: 'secret' });
    });
    // 验证 username 已持久化(非 password!)
    expect(localStorageMock.getItem('seafood-admin-ui:remember-username')).toBe('admin');
  });

  it('2.14:prefills username from previous remembered value', async () => {
    localStorageMock.setItem('seafood-admin-ui:remember-username', 'admin');
    renderWithProviders(<LoginPage />);
    const usernameInput = screen.getByLabelText('用户名') as HTMLInputElement;
    expect(usernameInput.value).toBe('admin');
    // remember checkbox 应自动勾上
    const rememberCheckbox = screen.getByLabelText(/记住我/) as HTMLInputElement;
    expect(rememberCheckbox.checked).toBe(true);
  });

  it('2.14:clears remembered username on submit when checkbox unchecked', async () => {
    const user = userEvent.setup();
    localStorageMock.setItem('seafood-admin-ui:remember-username', 'old-admin');
    renderWithProviders(<LoginPage />);
    // 默认 remember 是勾上的(因 storage 已有)— 取消勾选
    await user.click(screen.getByLabelText(/记住我/));
    await user.type(screen.getByLabelText('密码'), 'secret');
    await user.click(screen.getByRole('button', { name: '登录' }));
    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalled();
    });
    expect(localStorageMock.getItem('seafood-admin-ui:remember-username')).toBeNull();
  });
});
