/**
 * Auth feature: store.
 *
 * The auth store wraps the WeChat login flow:
 *   1. `wx.login({success})` to obtain a one-time `code`
 *   2. `POST /api/auth/wechat-login` with `{code}` → token pair + user
 *   3. Persist tokens via `tokenStorage` and the user via `getApp()`
 *   4. Notify subscribers so pages can refresh their UI
 *
 * On logout, tokens are cleared and the user is notified.
 *
 * On auto-refresh failure (raised by `request.ts`'s 401 path), the
 * store re-runs `wx.login` once. This is the spec's "Token refresh"
 * scenario.
 */

import {
  request,
  setOnAuthFailure,
  type WechatLoginResponse,
} from '../../shared/api/request';
import { tokenStorage, type StoredUser } from '../../shared/api/storage';
import { AuthAPI } from './api';

type Listener = (state: AuthState) => void;

export interface AuthState {
  user: StoredUser | null;
  isAuthenticated: boolean;
  isLoggingIn: boolean;
  lastError: string | null;
}

const STORAGE_USER_KEY = 'userInfo';

function getApp(): { globalData: { userInfo: StoredUser | null; token: string | null } } | null {
  if (typeof getApp === 'undefined') return null;
  return (globalThis as unknown as { getApp?: () => unknown }).getApp
    ? ((globalThis as unknown as { getApp: () => unknown }).getApp() as {
        globalData: { userInfo: StoredUser | null; token: string | null };
      })
    : null;
}

function loadUser(): StoredUser | null {
  const app = getApp();
  if (app?.globalData?.userInfo) return app.globalData.userInfo;
  // Fall back to wx storage (set by tokenStorage.setUser)
  if (typeof wx !== 'undefined' && wx.getStorageSync) {
    const v = wx.getStorageSync(STORAGE_USER_KEY);
    if (v && typeof v === 'object') return v as StoredUser;
  }
  return null;
}

function persistUser(user: StoredUser | null): void {
  tokenStorage.setUser(user);
  const app = getApp();
  if (app) {
    app.globalData.userInfo = user;
    app.globalData.token = user ? tokenStorage.getAccessToken() : null;
  }
}

class AuthStore {
  private state: AuthState = {
    user: loadUser(),
    isAuthenticated: !!loadUser(),
    isLoggingIn: false,
    lastError: null,
  };
  private listeners = new Set<Listener>();
  /** Single in-flight login promise, so concurrent `login()` calls
   *  share the same `wx.login` + `/auth/wechat-login` round-trip. */
  private loginInFlight: Promise<StoredUser> | null = null;

  constructor() {
    // Wire the central request layer so that when a refresh fails,
    // we re-run WeChat login in the background.
    setOnAuthFailure(() => {
      void this.silentRelogin();
    });
  }

  getState(): AuthState {
    return this.state;
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private setState(patch: Partial<AuthState>): void {
    this.state = { ...this.state, ...patch };
    this.listeners.forEach((l) => l(this.state));
  }

  /**
   * Run the WeChat login flow.
   *
   *   wx.login → POST /api/auth/wechat-login
   *
   * Persists tokens and the user, then returns the user.
   */
  async login(): Promise<StoredUser> {
    if (this.loginInFlight) return this.loginInFlight;
    this.loginInFlight = this.doLogin().finally(() => {
      this.loginInFlight = null;
    });
    return this.loginInFlight;
  }

  private async doLogin(): Promise<StoredUser> {
    this.setState({ isLoggingIn: true, lastError: null });
    try {
      const code = await this.wxLogin();
      const res = await AuthAPI.wechatLogin({ code });
      this.applyLoginResponse(res);
      this.setState({ isLoggingIn: false, lastError: null });
      return res.user;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'WeChat login failed';
      this.setState({ isLoggingIn: false, lastError: message });
      throw err;
    }
  }

  /** Wraps `wx.login` in a Promise and surfaces the `code`. */
  private wxLogin(): Promise<string> {
    return new Promise((resolve, reject) => {
      if (typeof wx === 'undefined' || !wx.login) {
        reject(new Error('wx.login is not available in this environment'));
        return;
      }
      wx.login({
        success: (res) => {
          if (res && res.code) resolve(res.code);
          else reject(new Error('wx.login did not return a code'));
        },
        fail: (err) => reject(new Error(err?.errMsg || 'wx.login failed')),
      });
    });
  }

  private applyLoginResponse(res: WechatLoginResponse): void {
    tokenStorage.setTokens(res.accessToken, res.refreshToken);
    persistUser(res.user);
    this.setState({
      user: res.user,
      isAuthenticated: true,
    });
  }

  /** Best-effort silent re-login used after a refresh failure. */
  private async silentRelogin(): Promise<StoredUser | null> {
    try {
      return await this.login();
    } catch {
      // The next user-initiated action will surface the error.
      this.setState({ isAuthenticated: false, user: null });
      return null;
    }
  }

  /**
   * Synchronous state reset for tests. Does NOT touch wx storage or
   * call the backend. Use `logout()` for the real flow.
   */
  resetForTest(): void {
    this.loginInFlight = null;
    this.setState({ user: null, isAuthenticated: false, lastError: null, isLoggingIn: false });
  }

  /**
   * Logout: clear tokens, notify the backend (best-effort),
   * reset local state.
   */
  async logout(): Promise<void> {
    try {
      await request({
        url: '/auth/logout',
        method: 'POST',
        needAuth: true,
        skipRefresh: true,
        timeout: 5000,
      });
    } catch {
      // best-effort
    }
    tokenStorage.clear();
    persistUser(null);
    this.setState({ user: null, isAuthenticated: false, lastError: null });
  }
}

export const authStore = new AuthStore();
export { AuthStore };
