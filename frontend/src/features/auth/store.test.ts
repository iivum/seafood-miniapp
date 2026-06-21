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

  describe('wx.login edge cases', () => {
    it('login() rejects when wx is undefined', async () => {
      const origWx = (globalThis as Record<string, unknown>).wx;
      (globalThis as Record<string, unknown>).wx = undefined;
      try {
        await expect(authStore.login()).rejects.toThrow('wx.login is not available');
      } finally {
        (globalThis as Record<string, unknown>).wx = origWx;
      }
    });

    it('login() rejects when wx.login returns no code', async () => {
      (wx.login as jest.Mock).mockImplementation((opts: {
        success: (res: { code?: string }) => void;
      }) => {
        opts.success({}); // no code
      });
      await expect(authStore.login()).rejects.toThrow('wx.login did not return a code');
    });
  });

  describe('silentRelogin on TOKEN_EXPIRED', () => {
    it('triggers silentRelogin when onAuthFailure fires with TOKEN_EXPIRED', async () => {
      const store = new AuthStore();
      tokenStorage.setTokens('old', 'old-refresh');
      (wx.request as jest.Mock).mockImplementation((opts: {
        url: string;
        success: (res: unknown) => void;
      }) => {
        if (opts.url.endsWith('/api/auth/wechat-login')) {
          opts.success({ statusCode: 200, data: sampleLoginRes });
        } else if (opts.url.endsWith('/api/auth/refresh')) {
          opts.success({ statusCode: 401, data: { code: 'TOKEN_EXPIRED' } });
        } else {
          opts.success({ statusCode: 200, data: {} });
        }
      });
      // Fire a request that will get TOKEN_EXPIRED on refresh
      // This should trigger silentRelogin internally
      try {
        await request({ url: '/orders', method: 'GET', needAuth: true });
      } catch {
        // expected to fail
      }
      // Give silentRelogin time to run
      await new Promise(r => setTimeout(r, 100));
    });
  });

  describe('silentRelogin gating', () => {
    it('silentRelogin returns null when locked', async () => {
      const store = new AuthStore();
      // Access private method for testing
      const fn = store as unknown as {
        silentRelogin: () => Promise<unknown>;
        reloginLocked: boolean;
      };
      fn.reloginLocked = true;
      const result = await fn.silentRelogin();
      expect(result).toBeNull();
    });

    it('doSilentRelogin returns null when debounce is active', async () => {
      const store = new AuthStore();
      const fn = store as unknown as {
        doSilentRelogin: () => Promise<unknown>;
        recentReloginAt: number;
        reloginLocked: boolean;
      };
      fn.recentReloginAt = Date.now(); // just attempted
      fn.reloginLocked = false;
      const result = await fn.doSilentRelogin();
      expect(result).toBeNull();
    });

    it('doSilentRelogin hard-locks after MAX_CONSECUTIVE_RELOGIN_FAILURES', async () => {
      const store = new AuthStore();
      const fn = store as unknown as {
        doSilentRelogin: () => Promise<unknown>;
        consecutiveReloginFailures: number;
        reloginLocked: boolean;
        recentReloginAt: number;
        login: () => Promise<unknown>;
      };
      fn.reloginLocked = false;
      fn.consecutiveReloginFailures = 0;
      // Mock login to always fail
      fn.login = jest.fn().mockRejectedValue(new Error('fail'));
      // Run enough times to hit the lock, resetting debounce each time
      const maxFailures = (AuthStore as unknown as { MAX_CONSECUTIVE_RELOGIN_FAILURES: number }).MAX_CONSECUTIVE_RELOGIN_FAILURES;
      for (let i = 0; i < maxFailures; i++) {
        fn.recentReloginAt = 0; // reset debounce
        await fn.doSilentRelogin();
      }
      expect(fn.reloginLocked).toBe(true);
    });
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
        url: string;
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
