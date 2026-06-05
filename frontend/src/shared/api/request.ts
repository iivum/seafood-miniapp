/**
 * Centralized request helper for the WeChat mini-program.
 *
 * Responsibilities (per design §8.3 / spec "Authentication and session"):
 *   - Single entry point: every feature uses `request()`.
 *   - Auto-injects the `Authorization: Bearer <accessToken>` header
 *     when `needAuth` is true.
 *   - On a 401 response, transparently calls
 *     `POST /api/auth/refresh` to mint a new token pair, retries the
 *     original request once, and re-queues any concurrent 401s.
 *   - Single-flight refresh: if N requests get 401 simultaneously,
 *     only one `/api/auth/refresh` call is made — the other N-1
 *     wait for it to complete.
 *   - On refresh failure, the tokens are cleared and a callback
 *     (`onAuthFailure`) is invoked so the auth store can re-run
 *     `wx.login`.
 *   - Surfaces backend `ErrorResponse` records as `ApiError` rejects
 *     so feature code can `instanceof ApiError` and inspect `.code`.
 */

import { tokenStorage, type StoredUser } from './storage';
import type { ApiErrorResponse, ApiResult } from './types';

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';

export interface RequestOptions {
  url: string;
  method?: HttpMethod;
  data?: unknown;
  header?: Record<string, string>;
  needAuth?: boolean;
  timeout?: number;
  /** Skip the auto-refresh-and-retry path. Default false. */
  skipRefresh?: boolean;
}

export class ApiError extends Error {
  readonly statusCode: number;
  readonly code: ApiErrorResponse['code'] | 'NETWORK';
  readonly fieldErrors?: Record<string, string>;
  readonly data: unknown;

  constructor(opts: {
    message: string;
    statusCode: number;
    code: ApiErrorResponse['code'] | 'NETWORK';
    fieldErrors?: Record<string, string>;
    data?: unknown;
  }) {
    super(opts.message);
    this.name = 'ApiError';
    this.statusCode = opts.statusCode;
    this.code = opts.code;
    if (opts.fieldErrors) this.fieldErrors = opts.fieldErrors;
    this.data = opts.data;
  }
}

export interface WechatLoginResponse {
  accessToken: string;
  refreshToken: string;
  user: StoredUser;
}

export interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
}

/* ------------------------------------------------------------------ */
/*  Config                                                             */
/* ------------------------------------------------------------------ */

let baseUrl = 'http://localhost:8080/api';

/** Override the base URL (used by tests, by `app.ts` onLaunch, etc.). */
export function setBaseUrl(url: string): void {
  baseUrl = url.replace(/\/+$/, '');
}

export function getBaseUrl(): string {
  return baseUrl;
}

/* ------------------------------------------------------------------ */
/*  Refresh single-flight                                               */
/* ------------------------------------------------------------------ */

let refreshInFlight: Promise<string | null> | null = null;

/** Test-only: clear the single-flight refresh promise. */
export function _resetRefreshInFlightForTest(): void {
  refreshInFlight = null;
}

/**
 * Called when a request gets a 401 that wasn't the refresh endpoint
 * itself. Performs the refresh once for the whole system, retrying
 * the original request after the new access token is available.
 */
async function performRefresh(): Promise<string | null> {
  if (refreshInFlight) return refreshInFlight;

  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) return null;

  refreshInFlight = (async () => {
    try {
      const res = await rawRequest<RefreshResponse>({
        url: '/auth/refresh',
        method: 'POST',
        data: { refreshToken },
        skipRefresh: true,
        timeout: 10000,
      });
      if (!res.data || !res.data.accessToken || !res.data.refreshToken) {
        return null;
      }
      tokenStorage.setTokens(res.data.accessToken, res.data.refreshToken);
      return res.data.accessToken;
    } catch {
      return null;
    } finally {
      // Reset on the next tick so concurrent 401s awaiting this
      // promise all observe the resolved value.
      setTimeout(() => {
        refreshInFlight = null;
      }, 0);
    }
  })();

  return refreshInFlight;
}

export type AuthFailureReason = 'REFRESH_FAILED' | 'NO_TOKEN';
export type AuthFailureHandler = (reason: AuthFailureReason) => void;
let onAuthFailure: AuthFailureHandler = () => {
  // default: silent; the auth store will re-run wx.login on next call
};

/** Register a callback for when the refresh path gives up. */
export function setOnAuthFailure(handler: AuthFailureHandler): void {
  onAuthFailure = handler;
}

/* ------------------------------------------------------------------ */
/*  Low-level wx.request wrapper                                       */
/* ------------------------------------------------------------------ */

interface RawSuccess<T> {
  statusCode: number;
  data: T;
}
interface RawFail {
  errMsg: string;
}

function callWx<T>(
  options: {
    url: string;
    method: HttpMethod;
    data: unknown;
    header: Record<string, string>;
    timeout: number;
  },
): Promise<RawSuccess<T> | RawFail> {
  return new Promise((resolve) => {
    if (typeof wx === 'undefined' || !wx.request) {
      // Test/non-mini-program environment — return a synthetic error
      // that the request layer will turn into a NETWORK ApiError.
      resolve({ errMsg: 'wx.request is not available' });
      return;
    }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    wx.request({
      ...options,
      success: (res: unknown) => {
        const r = res as RawSuccess<T>;
        resolve(r);
      },
      fail: (err: unknown) => {
        resolve(err as RawFail);
      },
    });
  });
}

function isWxFail(x: unknown): x is RawFail {
  return !!x && typeof x === 'object' && 'errMsg' in (x as Record<string, unknown>);
}

/* ------------------------------------------------------------------ */
/*  Raw request (no refresh)                                           */
/* ------------------------------------------------------------------ */

async function rawRequest<T>(options: RequestOptions): Promise<ApiResult<T>> {
  const {
    url,
    method = 'GET',
    data = undefined,
    header = {},
    needAuth = false,
    timeout = 30000,
  } = options;

  const requestHeader: Record<string, string> = {
    'Content-Type': 'application/json',
    ...header,
  };
  if (needAuth) {
    const token = tokenStorage.getAccessToken();
    if (token) requestHeader['Authorization'] = `Bearer ${token}`;
  }

  const fullUrl = url.startsWith('http') ? url : baseUrl + url;
  const result = await callWx<T>({
    url: fullUrl,
    method,
    data: data as unknown as object,
    header: requestHeader,
    timeout,
  });

  if (isWxFail(result)) {
    throw new ApiError({
      message: 'Network connection failed',
      statusCode: 0,
      code: 'NETWORK',
      data: result,
    });
  }

  const { statusCode, data: body } = result;
  if (statusCode >= 200 && statusCode < 300) {
    // Backend contract: success is the body itself (or a record
    // containing a `data` field — see existing `request.ts`). We
    // accept both shapes to keep historical callers working.
    const payload =
      body && typeof body === 'object' && 'data' in (body as Record<string, unknown>)
        ? ((body as Record<string, unknown>).data as T)
        : (body as T);
    return { data: payload, statusCode };
  }

  if (statusCode === 401) {
    // surface the body so the refresh layer can read ErrorResponse
    throw new ApiError({
      message: 'Unauthorized',
      statusCode: 401,
      code: classify401(body),
      data: body,
    });
  }

  // Other errors → map ErrorResponse
  const err = body as Partial<ApiErrorResponse> | undefined;
  throw new ApiError({
    message: err?.message || `Request failed: ${statusCode}`,
    statusCode,
    code: (err?.code as ApiErrorResponse['code']) || 'DOMAIN',
    fieldErrors: err?.fieldErrors,
    data: body,
  });
}

function classify401(body: unknown): ApiErrorResponse['code'] {
  if (body && typeof body === 'object') {
    const code = (body as Record<string, unknown>).code;
    if (code === 'TOKEN_EXPIRED' || code === 'TOKEN_INVALID' || code === 'TOKEN_REUSED') {
      return code;
    }
  }
  return 'TOKEN_EXPIRED';
}

/* ------------------------------------------------------------------ */
/*  Public request with refresh + retry                                */
/* ------------------------------------------------------------------ */

export async function request<T>(options: RequestOptions): Promise<T> {
  // First attempt
  try {
    const res = await rawRequest<T>(options);
    return res.data;
  } catch (err) {
    if (!(err instanceof ApiError) || err.statusCode !== 401 || options.skipRefresh) {
      throw err;
    }
  }

  // 401 path — try to refresh, then retry once
  const newAccess = await performRefresh();
  if (!newAccess) {
    tokenStorage.clear();
    onAuthFailure('REFRESH_FAILED');
    throw new ApiError({
      message: 'Session expired',
      statusCode: 401,
      code: 'TOKEN_EXPIRED',
    });
  }

  // Retry once with the new token (skipRefresh prevents recursion)
  const retry: RequestOptions = { ...options, skipRefresh: true };
  try {
    const res = await rawRequest<T>(retry);
    return res.data;
  } catch (err) {
    if (err instanceof ApiError && err.statusCode === 401) {
      // Refresh succeeded but the retry still 401s — give up.
      tokenStorage.clear();
      onAuthFailure('REFRESH_FAILED');
    }
    throw err;
  }
}

/* ------------------------------------------------------------------ */
/*  Convenience helpers (no `any`)                                     */
/* ------------------------------------------------------------------ */

export const get = <T,>(url: string, options?: Partial<RequestOptions>): Promise<T> =>
  request<T>({ ...options, url, method: 'GET' });

export const post = <T,>(url: string, data?: unknown, options?: Partial<RequestOptions>): Promise<T> =>
  request<T>({ ...options, url, method: 'POST', data });

export const put = <T,>(url: string, data?: unknown, options?: Partial<RequestOptions>): Promise<T> =>
  request<T>({ ...options, url, method: 'PUT', data });

export const del = <T,>(url: string, options?: Partial<RequestOptions>): Promise<T> =>
  request<T>({ ...options, url, method: 'DELETE' });

export const patch = <T,>(url: string, data?: unknown, options?: Partial<RequestOptions>): Promise<T> =>
  request<T>({ ...options, url, method: 'PATCH', data });
