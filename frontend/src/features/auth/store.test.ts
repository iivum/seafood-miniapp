import { authStore, AuthStore } from './store';
import { tokenStorage } from '../../shared/api/storage';
import {
  setBaseUrl,
  setOnAuthFailure,
  request,
  type WechatLoginResponse,
} from '../../shared/api/request';

function setWxLoginCode(code: string | null) {
  (wx.login as jest.Mock).mockImplementation((opts: {
    success: (res: { code?: string }) => void;
    fail: (err: unknown) => void;
  }) => {
    if (code) {
      opts.success({ code });
    } else {
      opts.fail({ errMsg: 'login fail' });
    }
  });
}

function setNextWxRequestResponse<T>(response: T | T[], statusCode = 200) {
  const responses = Array.isArray(response) ? response : [response];
  let i = 0;
  (wx.request as jest.Mock).mockImplementation((opts: {
    success: (res: unknown) => void;
    fail: (err: unknown) => void;
  }) => {
    const r = responses[i++] ?? { errMsg: 'no more mocks' };
    if (r && typeof r === 'object' && 'statusCode' in (r as Record<string, unknown>)) {
      opts.success(r);
    } else if (r && typeof r === 'object' && 'errMsg' in (r as Record<string, unknown>)) {
      opts.fail(r);
    } else {
      opts.success({ statusCode, data: r });
    }
  });
}

const sampleLoginRes: WechatLoginResponse = {
  accessToken: 'a-1',
  refreshToken: 'r-1',
  user: { id: 'u-1', nickname: 'Wx User', role: 'CUSTOMER' },
};

describe('features/auth/store', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    setBaseUrl('http://test.local/api');
    setOnAuthFailure(() => {
      /* default noop */
    });
    tokenStorage.clear();
    authStore.resetForTest();
  });

  it('login(): wx.login → POST /api/auth/wechat-login → persist tokens', async () => {
    setWxLoginCode('wx-code-1');
    setNextWxRequestResponse(sampleLoginRes);
    const user = await authStore.login();
    expect(user).toEqual(sampleLoginRes.user);
    expect(tokenStorage.getAccessToken()).toBe('a-1');
    expect(tokenStorage.getRefreshToken()).toBe('r-1');
    expect(authStore.getState().isAuthenticated).toBe(true);
    expect(authStore.getState().user).toEqual(sampleLoginRes.user);

    // Verify the HTTP call
    const calls = (wx.request as jest.Mock).mock.calls;
    expect(calls[0][0].url).toBe('http://test.local/api/auth/wechat-login');
    expect(calls[0][0].method).toBe('POST');
    expect(calls[0][0].data).toEqual({ code: 'wx-code-1' });
  });

  it('login() rejects when wx.login fails', async () => {
    setWxLoginCode(null);
    await expect(authStore.login()).rejects.toThrow();
    expect(authStore.getState().isAuthenticated).toBe(false);
    expect(authStore.getState().lastError).toBeTruthy();
  });

  it('login() is single-flight: concurrent calls share one wx.login + one request', async () => {
    setWxLoginCode('code');
    setNextWxRequestResponse(sampleLoginRes);
    const [a, b] = await Promise.all([authStore.login(), authStore.login()]);
    expect(a).toEqual(b);
    expect((wx.login as jest.Mock).mock.calls).toHaveLength(1);
    expect((wx.request as jest.Mock).mock.calls).toHaveLength(1);
  });

  it('subscribe: listeners receive state updates', async () => {
    const listener = jest.fn();
    const unsub = authStore.subscribe(listener);
    setWxLoginCode('code');
    setNextWxRequestResponse(sampleLoginRes);
    await authStore.login();
    expect(listener).toHaveBeenCalled();
    const last = listener.mock.calls[listener.mock.calls.length - 1][0];
    expect(last.isAuthenticated).toBe(true);
    unsub();
  });

  it('logout: clears tokens and resets state', async () => {
    setWxLoginCode('code');
    setNextWxRequestResponse([sampleLoginRes, { ok: true }]); // login then logout
    await authStore.login();
    expect(tokenStorage.getAccessToken()).toBe('a-1');
    await authStore.logout();
    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(authStore.getState().isAuthenticated).toBe(false);
  });

  it('logout: ignores backend logout failure (best-effort)', async () => {
    setWxLoginCode('code');
    // login OK, logout backend 500
    setNextWxRequestResponse([
      sampleLoginRes,
      { errMsg: 'network:fail' },
    ]);
    await authStore.login();
    await expect(authStore.logout()).resolves.toBeUndefined();
    expect(tokenStorage.getAccessToken()).toBeNull();
  });

  it('AuthStore is constructible independently', () => {
    const store = new AuthStore();
    expect(store.getState().isAuthenticated).toBe(false);
  });

  describe('security: silentRelogin gating (review #5)', () => {
    it('does NOT call wx.login when onAuthFailure is fired with TOKEN_REUSED', async () => {
      // Manually fire the registered onAuthFailure callback with a
      // non-EXPIRED code. silentRelogin must NOT trigger a login
      // round-trip; the store should hard-lock.
      const store = new AuthStore();
      const spy = jest.spyOn(store as unknown as { login: () => Promise<unknown> }, 'login');
      // Pull the registered handler out of the request module.
      // Easier: drive it through `request()` with a TOKEN_REUSED 401.
      tokenStorage.setTokens('old', 'old-refresh');
      (wx.request as jest.Mock).mockImplementation((opts: {
        success: (res: unknown) => void;
      }) => {
        if (opts.url === '/api/auth/wechat-login' || opts.url.endsWith('/api/auth/wechat-login')) {
          opts.success({ statusCode: 200, data: sampleLoginRes });
        } else if (opts.url.endsWith('/api/auth/refresh')) {
          opts.success({ statusCode: 200, data: { accessToken: 'new-a', refreshToken: 'new-r' } });
        } else {
          // original request 401 TOKEN_REUSED
          opts.success({ statusCode: 401, data: { code: 'TOKEN_REUSED' } });
        }
      });
      await expect(
        request({ url: '/orders', method: 'GET', needAuth: true }),
      ).rejects.toBeInstanceOf(Error);
      // No wx.login fired — the security signal short-circuits recovery.
      const wxLoginCalls = (wx.login as jest.Mock).mock.calls.length;
      expect(wxLoginCalls).toBe(0);
      spy.mockRestore();
    });
  });
});
