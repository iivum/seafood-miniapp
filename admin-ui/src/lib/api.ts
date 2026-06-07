import axios, { type AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import type { ApiError, TokenResponse } from '@/types/api';
import { useAuthStore } from '@/features/auth/store';

/**
 * Browser Axios instance.
 *  - withCredentials: true → backend sets HttpOnly+Secure cookies on
 *    /api/admin/auth/{login,refresh}. The browser sends them
 *    automatically on same-origin /api/** requests; JS never sees
 *    the refresh token (no XSS amplifier).
 *  - 401 interceptor → calls /api/admin/auth/refresh once (browser
 *    attaches the HttpOnly cookie), retries the original request
 *  - Request interceptor → attaches `Authorization: Bearer <access>`
 *    from the in-memory auth store for the 401-retry branch. The
 *    access token is short-lived (15 min) and kept only in memory.
 *  - On refresh failure → reject & caller (router guard) redirects
 *    to /admin/login.
 */
export const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15_000,
});

let refreshInFlight: Promise<TokenResponse> | null = null;

async function performRefresh(): Promise<TokenResponse> {
  // No body: the browser attaches the HttpOnly Secure refresh cookie
  // automatically. The server reads it, rotates it, and the browser
  // stores the rotated value (also HttpOnly Secure).
  const res = await axios.post<TokenResponse>(
    '/api/admin/auth/refresh',
    {},
    { withCredentials: true },
  );
  return res.data;
}

api.interceptors.request.use((config) => {
  // Attach Authorization from the in-memory access token when present.
  // The HttpOnly cookie alone is sufficient for cookie-auth endpoints;
  // this header is for backends that also accept Bearer (e.g. BFF
  // aggregation in dev) and for the 401-retry branch.
  const accessToken = useAuthStore.getState().accessToken;
  if (accessToken && config.headers) {
    config.headers['Authorization'] = `Bearer ${accessToken}`;
  }
  return config;
});

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
        // Update the in-memory store so subsequent requests carry the
        // new access token in the Authorization header.
        useAuthStore.getState().setSession(token);
        if (original.headers) {
          original.headers['Authorization'] = `Bearer ${token.accessToken}`;
        }
        return api.request(original);
      } catch (refreshError) {
        refreshInFlight = null;
        useAuthStore.getState().clear();
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  },
);

export type RequestConfig = AxiosRequestConfig;
