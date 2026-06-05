/**
 * Runtime shim for shared/api/request.ts — re-exports the compiled
 * TypeScript implementation. The WeChat mini-program runtime loads
 * `.js` files directly (no on-the-fly TS transpilation in this
 * project's setup), so the .ts file is mirrored here.
 *
 * The Jest test suite (ts-jest) covers the .ts; the .js is kept in
 * lockstep by hand for runtime use. Keep both files structurally
 * identical to avoid drift.
 */

const ACCESS_KEY = 'accessToken';
const REFRESH_KEY = 'refreshToken';
const USER_KEY = 'userInfo';

const hasWx = () => typeof wx !== 'undefined' && !!wx.getStorageSync;

const tokenStorage = {
  getAccessToken() {
    if (!hasWx()) return null;
    const v = wx.getStorageSync(ACCESS_KEY);
    return typeof v === 'string' && v.length > 0 ? v : null;
  },
  getRefreshToken() {
    if (!hasWx()) return null;
    const v = wx.getStorageSync(REFRESH_KEY);
    return typeof v === 'string' && v.length > 0 ? v : null;
  },
  setTokens(accessToken, refreshToken) {
    if (!hasWx()) return;
    wx.setStorageSync(ACCESS_KEY, accessToken);
    wx.setStorageSync(REFRESH_KEY, refreshToken);
  },
  setUser(user) {
    if (!hasWx()) return;
    if (user) {
      wx.setStorageSync(USER_KEY, user);
    } else {
      wx.removeStorageSync(USER_KEY);
    }
  },
  getUser() {
    if (!hasWx()) return null;
    const v = wx.getStorageSync(USER_KEY);
    return v && typeof v === 'object' ? v : null;
  },
  clear() {
    if (!hasWx()) return;
    wx.removeStorageSync(ACCESS_KEY);
    wx.removeStorageSync(REFRESH_KEY);
    wx.removeStorageSync(USER_KEY);
  },
};

let baseUrl = 'http://localhost:8080/api';

function setBaseUrl(url) {
  baseUrl = url.replace(/\/+$/, '');
}

function getBaseUrl() {
  return baseUrl;
}

let refreshInFlight = null;
let onAuthFailure = () => {};

function setOnAuthFailure(handler) {
  onAuthFailure = handler;
}

class ApiError extends Error {
  constructor(opts) {
    super(opts.message);
    this.name = 'ApiError';
    this.statusCode = opts.statusCode;
    this.code = opts.code;
    if (opts.fieldErrors) this.fieldErrors = opts.fieldErrors;
    this.data = opts.data;
  }
}

function callWx(options) {
  return new Promise((resolve) => {
    if (typeof wx === 'undefined' || !wx.request) {
      resolve({ errMsg: 'wx.request is not available' });
      return;
    }
    wx.request({
      ...options,
      success: (res) => resolve(res),
      fail: (err) => resolve(err),
    });
  });
}

function isWxFail(x) {
  return !!x && typeof x === 'object' && 'errMsg' in x;
}

function classify401(body) {
  if (body && typeof body === 'object') {
    const code = body.code;
    if (code === 'TOKEN_EXPIRED' || code === 'TOKEN_INVALID' || code === 'TOKEN_REUSED') {
      return code;
    }
  }
  return 'TOKEN_EXPIRED';
}

async function rawRequest(options) {
  const {
    url,
    method = 'GET',
    data = undefined,
    header = {},
    needAuth = false,
    timeout = 30000,
  } = options;

  const requestHeader = {
    'Content-Type': 'application/json',
    ...header,
  };
  if (needAuth) {
    const token = tokenStorage.getAccessToken();
    if (token) requestHeader['Authorization'] = `Bearer ${token}`;
  }

  const fullUrl = url.startsWith('http') ? url : baseUrl + url;
  const result = await callWx({
    url: fullUrl,
    method,
    data: data,
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
    const payload =
      body && typeof body === 'object' && 'data' in body ? body.data : body;
    return { data: payload, statusCode };
  }

  if (statusCode === 401) {
    throw new ApiError({
      message: 'Unauthorized',
      statusCode: 401,
      code: classify401(body),
      data: body,
    });
  }

  const err = body;
  throw new ApiError({
    message: (err && err.message) || `Request failed: ${statusCode}`,
    statusCode,
    code: (err && err.code) || 'DOMAIN',
    fieldErrors: err && err.fieldErrors,
    data: body,
  });
}

async function performRefresh() {
  if (refreshInFlight) return refreshInFlight;
  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) return null;

  refreshInFlight = (async () => {
    try {
      const res = await rawRequest({
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
      setTimeout(() => {
        refreshInFlight = null;
      }, 0);
    }
  })();

  return refreshInFlight;
}

async function request(options) {
  try {
    const res = await rawRequest(options);
    return res.data;
  } catch (err) {
    if (!(err instanceof ApiError) || err.statusCode !== 401 || options.skipRefresh) {
      throw err;
    }
  }

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

  const retry = { ...options, skipRefresh: true };
  try {
    const res = await rawRequest(retry);
    return res.data;
  } catch (err) {
    if (err instanceof ApiError && err.statusCode === 401) {
      tokenStorage.clear();
      onAuthFailure('REFRESH_FAILED');
    }
    throw err;
  }
}

const get = (url, options) => request({ ...options, url, method: 'GET' });
const post = (url, data, options) => request({ ...options, url, method: 'POST', data });
const put = (url, data, options) => request({ ...options, url, method: 'PUT', data });
const del = (url, options) => request({ ...options, url, method: 'DELETE' });
const patch = (url, data, options) => request({ ...options, url, method: 'PATCH', data });

module.exports = {
  ApiError,
  tokenStorage,
  setBaseUrl,
  getBaseUrl,
  setOnAuthFailure,
  request,
  get,
  post,
  put,
  del,
  patch,
};
