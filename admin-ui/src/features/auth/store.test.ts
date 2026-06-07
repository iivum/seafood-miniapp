import { describe, it, expect, beforeEach, vi } from 'vitest';
import { act } from '@testing-library/react';
import { useAuthStore } from './store';
import { api } from '@/lib/api';

vi.mock('@/lib/api', () => {
  const mockApi = {
    post: vi.fn(),
    get: vi.fn(),
  };
  return {
    api: mockApi,
    readCookie: vi.fn(),
    writeCookie: vi.fn(),
  };
});

const mockedApi = api as unknown as { post: ReturnType<typeof vi.fn>; get: ReturnType<typeof vi.fn> };

describe('auth store', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    document.cookie = 'admin_refresh_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
    act(() => {
      useAuthStore.setState({
        accessToken: null,
        username: null,
        role: null,
        hydrated: true,
      });
    });
  });

  it('starts unauthenticated', () => {
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    expect(useAuthStore.getState().hasRole('ADMIN')).toBe(false);
  });

  it('setSession populates role and username', () => {
    act(() => {
      useAuthStore.getState().setSession(
        {
          accessToken: 'a',
          refreshToken: 'r',
          accessTokenExpiresAt: new Date(Date.now() + 600_000).toISOString(),
          refreshTokenExpiresAt: new Date(Date.now() + 86_400_000).toISOString(),
          role: 'ADMIN',
        },
        'admin',
      );
    });
    expect(useAuthStore.getState().isAuthenticated()).toBe(true);
    expect(useAuthStore.getState().hasRole('ADMIN')).toBe(true);
  });

  it('clear() resets the session', () => {
    act(() => {
      useAuthStore.getState().setSession(
        {
          accessToken: 'a',
          refreshToken: 'r',
          accessTokenExpiresAt: new Date(Date.now() + 600_000).toISOString(),
          refreshTokenExpiresAt: new Date(Date.now() + 86_400_000).toISOString(),
          role: 'ADMIN',
        },
        'admin',
      );
    });
    act(() => {
      useAuthStore.getState().clear();
    });
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
  });

  it('login() calls POST /admin/auth/login and stores session', async () => {
    mockedApi.post.mockResolvedValueOnce({
      data: {
        accessToken: 'at',
        refreshToken: 'rt',
        accessTokenExpiresAt: new Date(Date.now() + 600_000).toISOString(),
        refreshTokenExpiresAt: new Date(Date.now() + 86_400_000).toISOString(),
        role: 'ADMIN',
      },
    });
    await act(async () => {
      await useAuthStore.getState().login({ username: 'admin', password: 'admin123' });
    });
    expect(mockedApi.post).toHaveBeenCalledWith('/admin/auth/login', { username: 'admin', password: 'admin123' });
    expect(useAuthStore.getState().username).toBe('admin');
    expect(useAuthStore.getState().role).toBe('ADMIN');
  });

  it('logout() clears session and hits logout endpoint', async () => {
    mockedApi.post.mockResolvedValueOnce({});
    act(() => {
      useAuthStore.getState().setSession(
        {
          accessToken: 'a',
          refreshToken: 'r',
          accessTokenExpiresAt: new Date(Date.now() + 600_000).toISOString(),
          refreshTokenExpiresAt: new Date(Date.now() + 86_400_000).toISOString(),
          role: 'ADMIN',
        },
        'admin',
      );
    });
    await act(async () => {
      await useAuthStore.getState().logout();
    });
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
  });
});
