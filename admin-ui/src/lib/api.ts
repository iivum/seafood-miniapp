import axios, { type AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import type { ApiError, RefreshRequest, TokenResponse } from '@/types/api';

/**
 * Browser Axios instance.
 *  - withCredentials: true → backend sets httpOnly cookie on /api/admin/auth/login
 *  - 401 interceptor → calls /api/admin/auth/refresh once, retries the original request
 *  - On refresh failure → reject & caller (e.g. router guard) handles redirect
 */
export const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15_000,
});

let refreshInFlight: Promise<TokenResponse> | null = null;

async function performRefresh(): Promise<TokenResponse> {
  // Read refresh token from a non-httpOnly "shadow" cookie set on login (see auth store).
  // Fallback: if not present, server-side cookie alone is used for the httpOnly side.
  const refreshToken = readCookie('admin_refresh_token');
  if (!refreshToken) {
    throw new Error('No refresh token available');
  }
  const body: RefreshRequest = { refreshToken };
  const res = await axios.post<TokenResponse>('/api/admin/auth/refresh', body, { withCredentials: true });
  return res.data;
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    if (
      error.response?.status === 401 &&
      original &&
      !original._retry &&
      original.url &&
      !original.url.includes('/admin/auth/')
    ) {
      original._retry = true;
      try {
        refreshInFlight = refreshInFlight ?? performRefresh();
        const token = await refreshInFlight;
        refreshInFlight = null;
        if (original.headers) {
          original.headers['Authorization'] = `Bearer ${token.accessToken}`;
        }
        return api.request(original);
      } catch (refreshError) {
        refreshInFlight = null;
        writeCookie('admin_refresh_token', '', 0);
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  },
);

export type RequestConfig = AxiosRequestConfig;

/** Read a cookie value by name (best-effort, document.cookie based). */
export function readCookie(name: string): string | null {
  if (typeof document === 'undefined') {
    return null;
  }
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1] ?? '') : null;
}

/** Write a cookie value with expiry in seconds. */
export function writeCookie(name: string, value: string, maxAgeSeconds: number): void {
  if (typeof document === 'undefined') {
    return;
  }
  document.cookie = `${name}=${encodeURIComponent(value)}; path=/; max-age=${maxAgeSeconds}; SameSite=Strict`;
}
