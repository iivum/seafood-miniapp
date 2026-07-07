/**
 * Token storage helpers.
 *
 * Tokens are stored under the keys `accessToken` and `refreshToken`
 * (per design §8.3 / spec "Authentication and session"). They survive
 * page reloads (sync storage) and are cleared by `clearTokens()` on
 * logout.
 */

const ACCESS_KEY = 'accessToken';
const REFRESH_KEY = 'refreshToken';
const USER_KEY = 'userInfo';

export interface StoredUser {
  id: string;
  nickname?: string;
  avatarUrl?: string;
  role?: 'CUSTOMER' | 'ADMIN' | string;
  openId?: string;
  phone?: string;
}

const hasWx = (): boolean => typeof wx !== 'undefined' && !!wx.getStorageSync;

export const tokenStorage = {
  getAccessToken(): string | null {
    if (!hasWx()) return null;
    const v = wx.getStorageSync(ACCESS_KEY);
    return typeof v === 'string' && v.length > 0 ? v : null;
  },
  getRefreshToken(): string | null {
    if (!hasWx()) return null;
    const v = wx.getStorageSync(REFRESH_KEY);
    return typeof v === 'string' && v.length > 0 ? v : null;
  },
  setTokens(accessToken: string, refreshToken: string): void {
    if (!hasWx()) return;
    wx.setStorageSync(ACCESS_KEY, accessToken);
    wx.setStorageSync(REFRESH_KEY, refreshToken);
  },
  setUser(user: StoredUser | null): void {
    if (!hasWx()) return;
    if (user) {
      wx.setStorageSync(USER_KEY, user);
    } else {
      wx.removeStorageSync(USER_KEY);
    }
  },
  getUser(): StoredUser | null {
    if (!hasWx()) return null;
    const v = wx.getStorageSync(USER_KEY);
    return v && typeof v === 'object' ? (v as StoredUser) : null;
  },
  clear(): void {
    if (!hasWx()) return;
    wx.removeStorageSync(ACCESS_KEY);
    wx.removeStorageSync(REFRESH_KEY);
    wx.removeStorageSync(USER_KEY);
  },
};
