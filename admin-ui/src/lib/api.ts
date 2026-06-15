import axios, { type AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import type { ApiError, TokenResponse } from '@/types/api';
import { useAuthStore } from '@/features/auth/store';

/**
 * Browser Axios instance.
 *
 * <p>路线图 2.15 改造(对齐 2.12 AdminCookieAuthController):
 * <ul>
 *   <li>{@code withCredentials: true} → 后端 {@code Set-Cookie: HttpOnly+Secure+SameSite=Lax}
 *       自动下发,浏览器 same-origin 自动回带,JS 永远拿不到 refresh token(防 XSS)。</li>
 *   <li>Request 拦截器:
 *     <ul>
 *       <li>{@code Authorization: Bearer <access>} — 从 in-memory store 拿,15 min 短期</li>
 *       <li>{@code X-CSRF-Token} — 非 GET 请求从后端 {@code GET /api/admin/auth/csrf} 拉,
 *           懒拉一次 + 401 强制刷新</li>
 *     </ul>
 *   </li>
 *   <li>Response 拦截器:
 *     <ul>
 *       <li>401(非 auth 端点)→ 调一次 {@code /api/admin/auth/refresh}(浏览器带 HttpOnly cookie),
 *           拿新 access 重放原请求;refresh 失败 → 清 store + 跳 {@code /admin/login}</li>
 *       <li>403 + code=CSRF_TOKEN_MISMATCH → 清 csrf token,重拉 + 重放原请求一次</li>
 *     </ul>
 *   </li>
 * </ul>
 */
export const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15_000,
});

let refreshInFlight: Promise<TokenResponse> | null = null;
let csrfToken: string | null = null;
let csrfInFlight: Promise<string> | null = null;

async function performRefresh(): Promise<TokenResponse> {
  const res = await axios.post<TokenResponse>(
    '/api/admin/auth/refresh',
    {},
    { withCredentials: true },
  );
  return res.data;
}

async function fetchCsrfToken(): Promise<string> {
  if (csrfToken) return csrfToken;
  csrfInFlight = csrfInFlight ?? axios.get<{ csrfToken: string }>(
    '/api/admin/auth/csrf',
    { withCredentials: true },
  ).then((res) => {
    csrfToken = res.data.csrfToken;
    csrfInFlight = null;
    return csrfToken;
  });
  return csrfInFlight;
}

function redirectToLogin(): void {
  // 用 window.location 而非 router.navigate:auth 失败时 react 状态可能已损坏,
  // 硬刷 + 跳 /admin/login 是最稳的"踢回登录"姿势。
  if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/admin/login')) {
    window.location.assign('/admin/login');
  }
}

api.interceptors.request.use(async (config) => {
  // 1) Authorization:从 in-memory store 拿 access token
  const accessToken = useAuthStore.getState().accessToken;
  if (accessToken && config.headers) {
    config.headers['Authorization'] = `Bearer ${accessToken}`;
  }
  // 2) CSRF:非 GET / HEAD 请求 + 当前没有 token → 懒拉
  const method = (config.method ?? 'get').toLowerCase();
  if (method !== 'get' && method !== 'head') {
    try {
      const token = await fetchCsrfToken();
      if (config.headers) {
        config.headers['X-CSRF-Token'] = token;
      }
    } catch {
      // 拉 CSRF 失败(网络/未登录):放行,后端会拒;
      // 不阻断 GET(GET 走 idempotent 路径,CSRF 不参与)
    }
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean; _csrfRetry?: boolean }) | undefined;

    // ----- 401:access token 过期 → 调 refresh 重试 -----
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
        useAuthStore.getState().setSession(token);
        if (original.headers) {
          original.headers['Authorization'] = `Bearer ${token.accessToken}`;
        }
        return api.request(original);
      } catch (refreshError) {
        refreshInFlight = null;
        useAuthStore.getState().clear();
        // 踢回登录页(2.15 设计)
        redirectToLogin();
        return Promise.reject(refreshError);
      }
    }

    // ----- 403 + CSRF mismatch:重拉 CSRF + 重放一次 -----
    if (
      error.response?.status === 403 &&
      original &&
      !original._csrfRetry &&
      error.response.data?.code === 'CSRF_TOKEN_MISMATCH'
    ) {
      original._csrfRetry = true;
      csrfToken = null; // 强制重拉
      try {
        const token = await fetchCsrfToken();
        if (original.headers) {
          original.headers['X-CSRF-Token'] = token;
        }
        return api.request(original);
      } catch {
        return Promise.reject(error);
      }
    }

    return Promise.reject(error);
  },
);

export type RequestConfig = AxiosRequestConfig;
