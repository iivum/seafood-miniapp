import {
  request,
  ApiError,
  setBaseUrl,
  setOnAuthFailure,
  get,
  post,
  put,
  del,
  patch,
  _resetRefreshInFlightForTest,
  type WechatLoginResponse,
} from './request';
import { tokenStorage } from './storage';

interface WxSuccess<T> {
  statusCode: number;
  data: T;
  errMsg?: string;
}
interface WxFail {
  errMsg: string;
}
type WxResponse<T> = WxSuccess<T> | WxFail;

function makeWxSuccess<T>(data: T, statusCode = 200): WxSuccess<T> {
  // mp 真实成功回调 res 含 errMsg "request:ok" —— 必须带上,否则测试无法守卫
  // isWxFail 退回 `'errMsg' in x`(那会把每个成功误判为 NETWORK fail,见 isWxFail 注释)。
  return { statusCode, data, errMsg: 'request:ok' };
}
function makeWxFail(msg = 'network:fail'): WxFail {
  return { errMsg: msg };
}

function setNextWxResponse<T>(response: WxResponse<T> | WxResponse<T>[]) {
  const responses = Array.isArray(response) ? response : [response];
  let i = 0;
  (wx.request as jest.Mock).mockImplementation((opts: {
    success: (res: unknown) => void;
    fail: (err: unknown) => void;
  }) => {
    const r = responses[i++] ?? makeWxFail('no more mocks');
    if ('statusCode' in r) {
      opts.success(r);
    } else {
      opts.fail(r);
    }
  });
}

describe('shared/api/request', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    setBaseUrl('http://test.local/api');
    setOnAuthFailure(() => {
      /* default noop */
    });
    tokenStorage.clear();
    _resetRefreshInFlightForTest();
  });

  it('GET request: builds URL, sends headers, returns body', async () => {
    setNextWxResponse(makeWxSuccess({ hello: 'world' }));
    const data = await request({ url: '/ping', method: 'GET' });
    expect(data).toEqual({ hello: 'world' });
    expect(wx.request).toHaveBeenCalledWith(
      expect.objectContaining({
        url: 'http://test.local/api/ping',
        method: 'GET',
        header: expect.objectContaining({ 'Content-Type': 'application/json' }),
      }),
    );
  });

  it('POST request: serializes body and unwraps {data:...} envelope', async () => {
    setNextWxResponse(makeWxSuccess({ data: { id: 1 } }));
    const data = await post<{ id: number }>('/items', { name: 'x' });
    expect(data).toEqual({ id: 1 });
    expect(wx.request).toHaveBeenCalledWith(
      expect.objectContaining({
        url: 'http://test.local/api/items',
        method: 'POST',
        data: { name: 'x' },
      }),
    );
  });

  it('attaches Authorization header when needAuth=true and a token is present', async () => {
    tokenStorage.setTokens('access-1', 'refresh-1');
    setNextWxResponse(makeWxSuccess({ ok: true }));
    await request({ url: '/me', method: 'GET', needAuth: true });
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.header.Authorization).toBe('Bearer access-1');
  });

  it('does NOT attach Authorization when needAuth=false', async () => {
    tokenStorage.setTokens('access-1', 'refresh-1');
    setNextWxResponse(makeWxSuccess({ ok: true }));
    await request({ url: '/public', method: 'GET' });
    const call = (wx.request as jest.Mock).mock.calls[0][0];
    expect(call.header.Authorization).toBeUndefined();
  });

  it('throws ApiError(NETWORK) when wx.request fails', async () => {
    setNextWxResponse(makeWxFail('timeout'));
    await expect(get('/x')).rejects.toBeInstanceOf(ApiError);
    try {
      await get('/x');
    } catch (err) {
      expect((err as ApiError).code).toBe('NETWORK');
      expect((err as ApiError).statusCode).toBe(0);
    }
  });

  it('throws ApiError with backend code on non-2xx', async () => {
    setNextWxResponse(
      makeWxSuccess({ code: 'VALIDATION', message: 'bad', fieldErrors: { x: 'y' } }, 400),
    );
    try {
      await post('/items', {});
    } catch (err) {
      const e = err as ApiError;
      expect(e.statusCode).toBe(400);
      expect(e.code).toBe('VALIDATION');
      expect(e.fieldErrors).toEqual({ x: 'y' });
    }
  });

  it('sanitizes ApiError.data — never stashes raw response body (review #8)', async () => {
    // A hostile backend that echoes the request's Authorization header
    // in the error body. The ApiError must NOT carry the raw body so
    // it doesn't leak into console.error in feature stores.
    const hostileBody = {
      code: 'DOMAIN',
      message: 'no',
      echoedAuth: 'Bearer attacker-supplied-token',
    };
    setNextWxResponse(makeWxSuccess(hostileBody, 409));
    try {
      await post('/items', {});
    } catch (err) {
      const e = err as ApiError & { data: Record<string, unknown> };
      // The data field is sanitized to the allowlist. The raw `echoedAuth`
      // field MUST NOT appear in data.
      expect(e.data).toBeDefined();
      if (e.data && typeof e.data === 'object') {
        expect('echoedAuth' in e.data).toBe(false);
        expect('code' in e.data).toBe(true);
        expect('message' in e.data).toBe(true);
      }
    }
  });

  it('caps ApiError.message to 200 chars (sanitization)', async () => {
    const longMessage = 'a'.repeat(5000);
    setNextWxResponse(makeWxSuccess({ code: 'DOMAIN', message: longMessage }, 500));
    try {
      await post('/items', {});
    } catch (err) {
      const e = err as ApiError;
      expect(e.message.length).toBeLessThanOrEqual(200);
    }
  });

  describe('auto refresh on 401', () => {
    it('refreshes once, retries the original request, returns the retry result', async () => {
      tokenStorage.setTokens('expired', 'refresh-1');
      // 1) original → 401, 2) refresh → ok, 3) retry → ok
      setNextWxResponse<unknown>([
        makeWxSuccess({ code: 'TOKEN_EXPIRED', message: 'expired' }, 401),
        makeWxSuccess({ accessToken: 'new-access', refreshToken: 'new-refresh' }),
        makeWxSuccess({ ok: true }),
      ]);
      const data = await request<{ ok: boolean }>({ url: '/orders', method: 'GET', needAuth: true });
      expect(data).toEqual({ ok: true });
      expect(tokenStorage.getAccessToken()).toBe('new-access');
      expect(tokenStorage.getRefreshToken()).toBe('new-refresh');
    });

    it('single-flight: concurrent 401s share one refresh call', async () => {
      tokenStorage.setTokens('expired', 'refresh-1');
      // Per-URL response sequence, so microtask ordering doesn't matter.
      const callCounts = new Map<string, number>();
      (wx.request as jest.Mock).mockImplementation((opts: {
        url: string;
        success: (res: unknown) => void;
        fail: (err: unknown) => void;
      }) => {
        const n = (callCounts.get(opts.url) ?? 0) + 1;
        callCounts.set(opts.url, n);
        if (opts.url === 'http://test.local/api/auth/refresh') {
          opts.success({ statusCode: 200, data: { accessToken: 'new', refreshToken: 'new' } });
          return;
        }
        if (n === 1) {
          opts.success({ statusCode: 401, data: { code: 'TOKEN_EXPIRED' } });
        } else {
          const body = opts.url.endsWith('/a') ? { ok: 'a' } : { ok: 'b' };
          opts.success({ statusCode: 200, data: body });
        }
      });
      const [a, b] = await Promise.all([
        request<{ ok: string }>({ url: '/a', method: 'GET', needAuth: true }),
        request<{ ok: string }>({ url: '/b', method: 'GET', needAuth: true }),
      ]);
      expect(a).toEqual({ ok: 'a' });
      expect(b).toEqual({ ok: 'b' });

      const calls = (wx.request as jest.Mock).mock.calls as unknown[][];
      const refreshCalls = calls.filter(
        (c) => (c[0] as { url?: string }).url === 'http://test.local/api/auth/refresh',
      );
      expect(refreshCalls).toHaveLength(1);
    });

    it('on refresh failure: clears tokens, fires onAuthFailure, throws', async () => {
      tokenStorage.setTokens('expired', 'refresh-1');
      setNextWxResponse([
        makeWxSuccess({ code: 'TOKEN_EXPIRED' }, 401),
        makeWxSuccess({ code: 'TOKEN_REUSED', message: 'revoked' }, 401),
      ]);
      const handler = jest.fn();
      setOnAuthFailure(handler);

      await expect(request({ url: '/x', method: 'GET', needAuth: true })).rejects.toBeInstanceOf(
        ApiError,
      );
      expect(tokenStorage.getAccessToken()).toBeNull();
      expect(tokenStorage.getRefreshToken()).toBeNull();
      expect(handler).toHaveBeenCalledWith(expect.objectContaining({ reason: 'REFRESH_FAILED' }));
    });

    it('on retry-after-refresh still 401: gives up and fires onAuthFailure', async () => {
      tokenStorage.setTokens('expired', 'refresh-1');
      // Per-URL: first /x call 401, refresh ok, retry on /x 401.
      const calls = new Map<string, number>();
      (wx.request as jest.Mock).mockImplementation((opts: {
        url: string;
        success: (res: unknown) => void;
        fail: (err: unknown) => void;
      }) => {
        const n = (calls.get(opts.url) ?? 0) + 1;
        calls.set(opts.url, n);
        if (opts.url === 'http://test.local/api/auth/refresh') {
          opts.success({ statusCode: 200, data: { accessToken: 'new', refreshToken: 'new' } });
          return;
        }
        if (opts.url === 'http://test.local/api/x') {
          if (n === 1) opts.success({ statusCode: 401, data: { code: 'TOKEN_EXPIRED' } });
          else opts.success({ statusCode: 401, data: { code: 'TOKEN_REUSED' } });
          return;
        }
        opts.fail({ errMsg: 'unexpected ' + opts.url });
      });
      const handler = jest.fn();
      setOnAuthFailure(handler);

      await expect(request({ url: '/x', method: 'GET', needAuth: true })).rejects.toBeInstanceOf(
        ApiError,
      );
      expect(handler).toHaveBeenCalledWith(
        expect.objectContaining({ reason: 'REFRESH_FAILED', code: 'TOKEN_REUSED' }),
      );
    });

    it('on retry-after-refresh returns 403 (role revoked): also gives up (review #2)', async () => {
      // Broader guard: any >=400 after refresh = terminal. Covers
      // 403 (role revoked) AND any other 4xx the retry surfaces.
      tokenStorage.setTokens('expired', 'refresh-1');
      const calls = new Map<string, number>();
      (wx.request as jest.Mock).mockImplementation((opts: {
        url: string;
        success: (res: unknown) => void;
        fail: (err: unknown) => void;
      }) => {
        const n = (calls.get(opts.url) ?? 0) + 1;
        calls.set(opts.url, n);
        if (opts.url === 'http://test.local/api/auth/refresh') {
          opts.success({ statusCode: 200, data: { accessToken: 'new', refreshToken: 'new' } });
          return;
        }
        if (opts.url === 'http://test.local/api/x') {
          if (n === 1) opts.success({ statusCode: 401, data: { code: 'TOKEN_EXPIRED' } });
          else opts.success({ statusCode: 403, data: { code: 'DOMAIN', message: 'forbidden' } });
          return;
        }
        opts.fail({ errMsg: 'unexpected ' + opts.url });
      });
      const handler = jest.fn();
      setOnAuthFailure(handler);

      await expect(request({ url: '/x', method: 'GET', needAuth: true })).rejects.toBeInstanceOf(
        ApiError,
      );
      expect(handler).toHaveBeenCalledWith(
        expect.objectContaining({ reason: 'REFRESH_FAILED' }),
      );
    });

    it('does NOT trigger refresh when skipRefresh is true', async () => {
      tokenStorage.setTokens('expired', 'refresh-1');
      setNextWxResponse(makeWxSuccess({ code: 'TOKEN_EXPIRED' }, 401));
      await expect(
        request({ url: '/x', method: 'GET', needAuth: true, skipRefresh: true }),
      ).rejects.toBeInstanceOf(ApiError);
      // Only the original call, no refresh attempt
      expect((wx.request as jest.Mock).mock.calls).toHaveLength(1);
    });
  });

  it('WechatLoginResponse shape is exported', () => {
    const sample: WechatLoginResponse = {
      accessToken: 'a',
      refreshToken: 'r',
      user: { id: 'u1', role: 'CUSTOMER' },
    };
    expect(sample.accessToken).toBe('a');
  });

  it('put / del / patch convenience helpers route to the right methods', async () => {
    setNextWxResponse([
      makeWxSuccess({ ok: 'put' }),
      makeWxSuccess({ ok: 'del' }),
      makeWxSuccess({ ok: 'patch' }),
    ]);
    await put('/x/1', { v: 1 });
    await del('/x/1');
    await patch('/x/1', { v: 2 });
    const calls = (wx.request as jest.Mock).mock.calls;
    expect(calls[0][0].method).toBe('PUT');
    expect(calls[1][0].method).toBe('DELETE');
    expect(calls[2][0].method).toBe('PATCH');
  });
});
