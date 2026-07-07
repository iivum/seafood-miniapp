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
      await this._applyLoginResponse(res);
      this._setState({ isLoggingIn: false, lastError: null });
      return this.state.user;
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
      await this._applyLoginResponse(res);
      this._setState({ isLoggingIn: false, lastError: null });
      return this.state.user;
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

  /**
   * 后端 POST /auth/wechat-login 响应体(TokenResponse.java)只有 token 相关
   * 字段,从来没有 user 字段——res.user 恒为 undefined。这里补调
   * GET /users/me 拿真实用户信息。该附加请求是非致命性的:失败(网络错误等)
   * 时静默降级为 user: null,不 throw,不阻断登录主流程(token 已拿到,核心
   * 功能应可用,只是用户信息展示缺失)。参照 logout() 的 best-effort 模式。
   */
  async _applyLoginResponse(res) {
    tokenStorage.setTokens(res.accessToken, res.refreshToken);
    let user = res.user || null;
    if (!user) {
      try {
        const { UserAPI } = require('../user/api');
        user = await UserAPI.me();
      } catch {
        user = null; // best-effort:附加请求失败不阻断登录
      }
    }
    persistUser(user);
    this._setState({ user, isAuthenticated: true });
  }

  /**
   * 手机号绑定:调 UserAPI.bindPhone(code)(dev fallback 合成 dev- 前缀 code,
   * 同 loginWithCode 惯例),把返回的 phone 合并进现有 state.user(只改 phone
   * 字段,不整体替换)。
   */
  async bindPhone(code) {
    const { UserAPI } = require('../user/api');
    const updated = await UserAPI.bindPhone(code);
    const merged = Object.assign({}, this.state.user || { id: updated.id }, { phone: updated.phone });
    persistUser(merged);
    this._setState({ user: merged });
    return merged;
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
