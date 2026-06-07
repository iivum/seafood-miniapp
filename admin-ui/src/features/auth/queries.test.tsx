import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useLogin } from './queries';
import { useAuthStore } from './store';
import { api } from '@/lib/api';
import { AxiosError } from 'axios';

vi.mock('@/lib/api', () => ({
  api: { post: vi.fn() },
  readCookie: vi.fn(),
  writeCookie: vi.fn(),
}));

const mockedApi = api as unknown as { post: ReturnType<typeof vi.fn> };

function makeWrapper() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

describe('useLogin', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ username: null, role: null, hydrated: true });
  });

  it('propagates ApiError.message from backend', async () => {
    const err = Object.assign(new AxiosError('request failed'), {
      response: { status: 401, data: { code: 'TOKEN_INVALID', message: '凭据无效' } },
    });
    mockedApi.post.mockRejectedValueOnce(err);
    const { result } = renderHook(() => useLogin(), { wrapper: makeWrapper() });
    let caught: Error | undefined;
    await act(async () => {
      try {
        await result.current.mutateAsync({ username: 'admin', password: 'wrong' });
      } catch (e) {
        caught = e as Error;
      }
    });
    expect(caught).toBeInstanceOf(Error);
    expect(caught?.message).toBe('凭据无效');
  });

  it('falls back to a generic message when no ApiError body is present', async () => {
    mockedApi.post.mockRejectedValueOnce(new Error('Network down'));
    const { result } = renderHook(() => useLogin(), { wrapper: makeWrapper() });
    let caught: Error | undefined;
    await act(async () => {
      try {
        await result.current.mutateAsync({ username: 'admin', password: 'x' });
      } catch (e) {
        caught = e as Error;
      }
    });
    expect(caught?.message).toBe('Network down');
  });
});
