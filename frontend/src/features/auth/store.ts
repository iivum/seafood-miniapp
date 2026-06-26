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
 * store MAY re-run `wx.login` once — but ONLY if the failure was
 * `TOKEN_EXPIRED`. `TOKEN_REUSED` and `TOKEN_INVALID` are security
 * signals (refresh token replayed / malformed) and must not trigger
 * a silent re-login. This is the spec's "Token refresh" scenario
 * with the security review's fail-open-loop guard.
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
  /** Single in-flight silentRelogin promise, so concurrent
   *  setOnAuthFailure callbacks share one attempt. */
  private silentReloginInFlight: Promise<StoredUser | null> | null = null;
  /** Timestamp (ms) of the last silentRelogin attempt. Used to debounce
   *  a thrashing 401 stream (e.g. backend down) so we don't loop. */
  private recentReloginAt = 0;
  /** Count of consecutive silentRelogin failures. Caps and hard-locks
   *  after MAX_CONSECUTIVE_RELOGIN_FAILURES to prevent a fail-open loop
   *  in a token-theft / DoS scenario. */
  private consecutiveReloginFailures = 0;
  /** Hard lock — once set, only an explicit user login() can resume. */
  private reloginLocked = false;
  private static readonly RELOGIN_DEBOUNCE_MS = 30_000;
  private static readonly MAX_CONSECUTIVE_RELOGIN_FAILURES = 3;

  constructor() {
    // Wire the central request layer so that when a refresh fails,
    // we MAY re-run WeChat login in the background. silentRelogin is
    // gated on TOKEN_EXPIRED only (not TOKEN_REUSED / TOKEN_INVALID),
    // deduplicated, debounced, and capped to prevent a fail-open loop.
    setOnAuthFailure((detail) => {
      // Security: only TOKEN_EXPIRED is auto-recoverable. Reuse and
      // invalid are signals that the refresh token may be in attacker
      // hands — require explicit user action.
      if (detail.code !== 'TOKEN_EXPIRED') {
        this.setState({ isAuthenticated: false, user: null });
        this.reloginLocked = true;
        return;
      }
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

  /**
   * Run the WeChat login flow using an externally-supplied `code`.
   *
   * Unlike `login()`, this does NOT call `wx.login` internally — the caller
   * provides the code. Used by the login page's two paths:
   *   - 开发者登录(dev-login):caller synthesizes a `dev-…` code that the
   *     backend recognises in dev mode and treats as a test openId.
   *   - 微信登录:caller calls `wx.login` first and forwards the real code
   *     for `jscode2session`.
   *
   * Single-flight deduped against concurrent `login()` calls so the
   * dev-login path shares the same in-flight promise as the regular path.
   */
  async loginWithCode(code: string): Promise<StoredUser> {
    if (!code || typeof code !== 'string') {
      throw new Error('loginWithCode requires a non-empty code');
    }
    if (this.loginInFlight) return this.loginInFlight;
    this.loginInFlight = this.doLoginWithCode(code).finally(() => {
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

  private async doLoginWithCode(code: string): Promise<StoredUser> {
    this.setState({ isLoggingIn: true, lastError: null });
    try {
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
    // A successful login resets the silent-relogin failure counter
    // and clears any prior hard lock.
    this.consecutiveReloginFailures = 0;
    this.reloginLocked = false;
    this.recentReloginAt = 0;
    this.setState({
      user: res.user,
      isAuthenticated: true,
    });
  }

  /**
   * Best-effort silent re-login used after a refresh failure.
   *
   * Guarded against a fail-open loop:
   *  - only invoked for TOKEN_EXPIRED (setOnAuthFailure filters
   *    TOKEN_REUSED / TOKEN_INVALID into hard-lock).
   *  - deduplicated: concurrent calls share one promise.
   *  - 30s debounce: ignores re-invocations within 30s of the last attempt.
   *  - 3-strikes hard lock: after MAX_CONSECUTIVE_RELOGIN_FAILURES
   *    consecutive failures, the store enters a hard-locked state where
   *    only an explicit user-initiated login() can resume. The user
   *    sees a logged-out UI; no background wx.login thrash.
   */
  private async silentRelogin(): Promise<StoredUser | null> {
    if (this.reloginLocked) return null;
    if (this.silentReloginInFlight) return this.silentReloginInFlight;
    this.silentReloginInFlight = this.doSilentRelogin().finally(() => {
      this.silentReloginInFlight = null;
    });
    return this.silentReloginInFlight;
  }

  private async doSilentRelogin(): Promise<StoredUser | null> {
    if (this.reloginLocked) return null;
    const now = Date.now();
    if (now - this.recentReloginAt < AuthStore.RELOGIN_DEBOUNCE_MS) return null;
    this.recentReloginAt = now;
    try {
      const user = await this.login();
      this.consecutiveReloginFailures = 0;
      return user;
    } catch {
      this.consecutiveReloginFailures += 1;
      if (this.consecutiveReloginFailures >= AuthStore.MAX_CONSECUTIVE_RELOGIN_FAILURES) {
        this.reloginLocked = true;
        this.setState({ isAuthenticated: false, user: null });
      }
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
