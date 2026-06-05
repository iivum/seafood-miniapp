import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act } from '@testing-library/react';
import { useAuthStore } from './store';
import { api, readCookie } from '@/lib/api';

vi.mock('@/lib/api', () => ({
  api: { post: vi.fn(), get: vi.fn() },
  readCookie: vi.fn(),
  writeCookie: vi.fn(),
}));

const mockedApi = api as unknown as { post: ReturnType<typeof vi.fn>; get: ReturnType<typeof vi.fn> };
const mockedReadCookie = readCookie as unknown as ReturnType<typeof vi.fn>;

describe('auth store: refresh and loadSession', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ username: null, role: null, hydrated: true, accessToken: null });
  });

  it('refresh() returns false when no cookie is present', async () => {
    mockedReadCookie.mockReturnValueOnce(null);
    let ok: boolean | undefined;
    await act(async () => {
      ok = await useAuthStore.getState().refresh();
    });
    expect(ok).toBe(false);
  });

  it('refresh() returns true and stores new tokens on success', async () => {
    mockedReadCookie.mockReturnValueOnce('shadow');
    mockedApi.post.mockResolvedValueOnce({
      data: {
        accessToken: 'new-at',
        refreshToken: 'new-rt',
        accessTokenExpiresAt: new Date(Date.now() + 600_000).toISOString(),
        refreshTokenExpiresAt: new Date(Date.now() + 86_400_000).toISOString(),
        role: 'ADMIN',
      },
    });
    let ok: boolean | undefined;
    await act(async () => {
      ok = await useAuthStore.getState().refresh();
    });
    expect(ok).toBe(true);
    expect(useAuthStore.getState().role).toBe('ADMIN');
  });

  it('refresh() returns false and clears state on server error', async () => {
    mockedReadCookie.mockReturnValueOnce('shadow');
    mockedApi.post.mockRejectedValueOnce(new Error('refresh failed'));
    let ok: boolean | undefined;
    await act(async () => {
      ok = await useAuthStore.getState().refresh();
    });
    expect(ok).toBe(false);
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);
  });

  it('loadSession() returns null when not authenticated and refresh fails', async () => {
    mockedReadCookie.mockReturnValueOnce(null);
    let res: { username: string; role: string } | null | undefined;
    await act(async () => {
      res = await useAuthStore.getState().loadSession();
    });
    expect(res).toBeNull();
  });

  it('loadSession() returns user info when already authenticated', async () => {
    useAuthStore.setState({ username: 'admin', role: 'ADMIN', hydrated: true });
    mockedApi.get.mockResolvedValueOnce({ data: { id: 'u-1' } });
    let res: { username: string; role: string } | null | undefined;
    await act(async () => {
      res = await useAuthStore.getState().loadSession();
    });
    expect(res).toEqual({ username: 'admin', role: 'ADMIN' });
  });
});
