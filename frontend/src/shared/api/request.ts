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
 *     (`onAuthFailure`) is invoked with the classified 401 code so
 *     the auth store can decide whether auto-recovery is safe.
 *   - Surfaces backend `ErrorResponse` records as `ApiError` rejects
 *     so feature code can `instanceof ApiError` and inspect `.code`.
 *
 * Security: `ApiError.data` is sanitized to a fixed allowlist. The raw
 * response body is never stashed — backend errors may echo request data
 * (incl. tokens in pathological cases) and we don't want that in
 * thrown objects that get console.error-logged in feature stores.
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
  /**
   * Sanitized snapshot of the backend error. Only contains fields from
   * a fixed allowlist (`code`, `message`, `fieldErrors`) — never the
   * raw body, which may echo request data.
   */
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
/** Snapshot of the classified 401 code that triggered the failure.
 *  Used by the auth store to decide whether auto-recovery is safe
 *  (only TOKEN_EXPIRED is recoverable; TOKEN_REUSED / TOKEN_INVALID
 *  are security signals that must NOT trigger a silent wx.login). */
export type AuthFailureCode = ApiErrorResponse['code'] | 'NETWORK';
export interface AuthFailureDetail {
  reason: AuthFailureReason;
  code?: AuthFailureCode;
}
export type AuthFailureHandler = (detail: AuthFailureDetail) => void;
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
  // mp 的 wx.request 成功回调 res 也含 errMsg("request:ok") → 不能用 `'errMsg' in x` 判失败,
  // 否则每个成功响应都被误判为 NETWORK fail(C5 mp-08/mp-03 实证:order/product 数据全加载不出)。
  // 失败结果无 statusCode,成功必有 number statusCode → 以此区分。
  return !x || typeof x !== 'object' || typeof (x as { statusCode?: unknown }).statusCode !== 'number';
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

  // Sanitize the error: build an allowlist of safe fields. Never
  // stash the raw body — it may echo request data in pathological
  // backend error paths.
  const safeErr = sanitizeErrorBody(body, statusCode);

  if (statusCode === 401) {
    throw new ApiError({
      message: 'Unauthorized',
      statusCode: 401,
      code: classify401(body),
      data: safeErr,
    });
  }

  throw new ApiError({
    message: (safeErr && safeErr.message) || `Request failed: ${statusCode}`,
    statusCode,
    code: (safeErr && safeErr.code) || 'DOMAIN',
    fieldErrors: safeErr?.fieldErrors,
    data: safeErr,
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

/** Pick only the safe fields from a backend error body. Drops anything
 *  not in the allowlist so the raw body never leaks into thrown
 *  ApiError objects (which get logged/inspected downstream). */
function sanitizeErrorBody(
  body: unknown,
  statusCode: number,
): { code?: ApiErrorResponse['code']; message?: string; fieldErrors?: Record<string, string> } | null {
  if (!body || typeof body !== 'object') {
    return statusCode >= 500 ? null : null;
  }
  const b = body as Record<string, unknown>;
  const out: { code?: ApiErrorResponse['code']; message?: string; fieldErrors?: Record<string, string> } = {};
  if (typeof b.code === 'string') out.code = b.code as ApiErrorResponse['code'];
  if (typeof b.message === 'string') {
    // Cap to 200 chars of safe characters to avoid a hostile backend
    // pushing megabytes of log lines into every ApiError.
    out.message = b.message.slice(0, 200);
  }
  if (b.fieldErrors && typeof b.fieldErrors === 'object') {
    const fe: Record<string, string> = {};
    for (const [k, v] of Object.entries(b.fieldErrors as Record<string, unknown>)) {
      if (typeof v === 'string') fe[k] = v.slice(0, 200);
    }
    if (Object.keys(fe).length) out.fieldErrors = fe;
  }
  return out;
}

/* ------------------------------------------------------------------ */
/*  Public request with refresh + retry                                */
/* ------------------------------------------------------------------ */

export async function request<T>(options: RequestOptions): Promise<T> {
  // First attempt
  let firstErr: ApiError | null = null;
  try {
    const res = await rawRequest<T>(options);
    return res.data;
  } catch (err) {
    if (!(err instanceof ApiError) || err.statusCode !== 401 || options.skipRefresh) {
      throw err;
    }
    firstErr = err;
  }

  // 401 path — try to refresh, then retry once
  const newAccess = await performRefresh();
  if (!newAccess) {
    const code = firstErr?.code && firstErr.code !== 'NETWORK'
      ? (firstErr.code as ApiErrorResponse['code'])
      : 'TOKEN_EXPIRED';
    tokenStorage.clear();
    onAuthFailure({ reason: 'REFRESH_FAILED', code });
    throw new ApiError({
      message: 'Session expired',
      statusCode: 401,
      code,
    });
  }

  // Retry once with the new token (skipRefresh prevents recursion)
  const retry: RequestOptions = { ...options, skipRefresh: true };
  try {
    const res = await rawRequest<T>(retry);
    return res.data;
  } catch (err) {
    if (err instanceof ApiError && err.statusCode >= 400) {
      // Refresh succeeded but the retry still 4xx/5xx — treat the
      // session as suspect. Covers 401 (token revoked) AND 403
      // (role revoked) AND any 4xx the retry surfaces.
      tokenStorage.clear();
      onAuthFailure({
        reason: 'REFRESH_FAILED',
        code: err.code === 'NETWORK' ? err.code : (err.code as ApiErrorResponse['code']),
      });
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
