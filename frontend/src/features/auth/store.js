/**
 * Runtime shim for features/auth/store.ts.
 *
 * WeChat mini-program runtime loads `.js`; the .ts is the type-checked
 * source of truth used by Jest.
 */
const { setOnAuthFailure, request, tokenStorage } = require('../../shared/api/request');

function getApp() {
  // The WeChat runtime injects a global `getApp()` function. In Jest
  // (or plain Node) it's defined on `globalThis` by jest.setup.js.
  if (typeof globalThis !== 'undefined' && typeof globalThis.getApp === 'function') {
    return globalThis.getApp();
  }
  return null;
}

function loadUser() {
  const app = getApp();
  if (app && app.globalData && app.globalData.userInfo) return app.globalData.userInfo;
  if (typeof wx !== 'undefined' && wx.getStorageSync) {
    const v = wx.getStorageSync('userInfo');
    if (v && typeof v === 'object') return v;
  }
  return null;
}

function persistUser(user) {
  tokenStorage.setUser(user);
  const app = getApp();
  if (app) {
    app.globalData.userInfo = user;
    app.globalData.token = user ? tokenStorage.getAccessToken() : null;
  }
}

class AuthStore {
  constructor() {
    this.state = {
      user: loadUser(),
      isAuthenticated: !!loadUser(),
      isLoggingIn: false,
      lastError: null,
    };
    this.listeners = new Set();
    this.loginInFlight = null;
    setOnAuthFailure(() => {
      void this.silentRelogin();
    });
  }

  getState() {
    return this.state;
  }

  subscribe(listener) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  _setState(patch) {
    this.state = { ...this.state, ...patch };
    this.listeners.forEach((l) => l(this.state));
  }

  resetForTest() {
    this.loginInFlight = null;
    this._setState({ user: null, isAuthenticated: false, lastError: null, isLoggingIn: false });
  }

  async login() {
    if (this.loginInFlight) return this.loginInFlight;
    this.loginInFlight = this._doLogin().finally(() => {
      this.loginInFlight = null;
    });
    return this.loginInFlight;
  }

  /**
   * 登录:外部传入 code(开发者登录合成 dev- 前缀,微信登录经 wx.login 拿真 code)。
   * 与 login() 共用 loginInFlight,避免并发重复请求。
   */
  async loginWithCode(code) {
    if (!code || typeof code !== 'string') {
      throw new Error('loginWithCode requires a non-empty code');
    }
    if (this.loginInFlight) return this.loginInFlight;
    this.loginInFlight = this._doLoginWithCode(code).finally(() => {
      this.loginInFlight = null;
    });
    return this.loginInFlight;
  }

  async _doLogin() {
    this._setState({ isLoggingIn: true, lastError: null });
    try {
      const code = await this._wxLogin();
      const { AuthAPI } = require('./api');
      const res = await AuthAPI.wechatLogin({ code });
      this._applyLoginResponse(res);
      this._setState({ isLoggingIn: false, lastError: null });
      return res.user;
    } catch (err) {
      const message = err && err.message ? err.message : 'WeChat login failed';
      this._setState({ isLoggingIn: false, lastError: message });
      throw err;
    }
  }

  async _doLoginWithCode(code) {
    this._setState({ isLoggingIn: true, lastError: null });
    try {
      const { AuthAPI } = require('./api');
      const res = await AuthAPI.wechatLogin({ code });
      this._applyLoginResponse(res);
      this._setState({ isLoggingIn: false, lastError: null });
      return res.user;
    } catch (err) {
      const message = err && err.message ? err.message : 'WeChat login failed';
      this._setState({ isLoggingIn: false, lastError: message });
      throw err;
    }
  }

  _wxLogin() {
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
        fail: (err) => reject(new Error((err && err.errMsg) || 'wx.login failed')),
      });
    });
  }

  _applyLoginResponse(res) {
    tokenStorage.setTokens(res.accessToken, res.refreshToken);
    persistUser(res.user);
    this._setState({ user: res.user, isAuthenticated: true });
  }

  async silentRelogin() {
    try {
      return await this.login();
    } catch {
      this._setState({ isAuthenticated: false, user: null });
      return null;
    }
  }

  async logout() {
    try {
      await request({
        url: '/auth/logout',
        method: 'POST',
        needAuth: true,
        skipRefresh: true,
        timeout: 5000,
      });
    } catch {
      /* best-effort */
    }
    tokenStorage.clear();
    persistUser(null);
    this._setState({ user: null, isAuthenticated: false, lastError: null });
  }
}

const authStore = new AuthStore();
module.exports = { authStore, AuthStore };
