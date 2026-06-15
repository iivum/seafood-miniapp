/**
 * Jest setup file for mini-program testing
 * Provides mocks for wx API and other mini-program globals
 */

// In-memory backing store for the wx storage mocks so setStorageSync
// followed by getStorageSync returns what was set.
const wxStorage = new Map();

global.wx = {
  request: jest.fn(),
  showToast: jest.fn(),
  showLoading: jest.fn(),
  hideLoading: jest.fn(),
  showModal: jest.fn(),
  login: jest.fn(),
  getUserInfo: jest.fn(),
  setStorageSync: jest.fn((key, value) => {
    wxStorage.set(key, value);
  }),
  getStorageSync: jest.fn((key) => wxStorage.get(key) ?? ''),
  removeStorageSync: jest.fn((key) => {
    wxStorage.delete(key);
  }),
  reLaunch: jest.fn(),
  navigateTo: jest.fn(),
  navigateBack: jest.fn(),
  redirectTo: jest.fn(),
  switchTab: jest.fn(),
  pageScrollTo: jest.fn(),
  stopPullDownRefresh: jest.fn(),
  onWindowResize: jest.fn(),
  onKeyUp: jest.fn(),
  onKeyDown: jest.fn(),
  getSystemInfoSync: jest.fn(() => ({
    SDKVersion: '3.0.0',
    version: '8.0.0',
    brand: 'devtools',
    model: 'iPhone 14',
    platform: 'ios',
    windowWidth: 375,
    windowHeight: 667,
    pixelRatio: 2,
    statusBarHeight: 20,
    safeArea: { top: 44, bottom: 778, left: 0, right: 375, width: 375, height: 734 },
  })),
};

// Mock getApp function
global.getApp = jest.fn(() => ({
  globalData: {
    baseUrl: 'http://localhost:8080/api',
    token: null,
    userInfo: null,
  },
}));

// Mock WeChat Component global(在 WeChat runtime 由框架注入;Node 测试环境 stub)
// 测试组件时只 import 纯逻辑(`./actions` 等),`./index` 内的 Component({...}) 也能
// 在 Node 环境下 import 不抛错。
global.Component = global.Component || function Component() {};

// Mock console methods to reduce noise in tests
global.console.warn = jest.fn();
global.console.error = jest.fn();

// Reset all mocks before each test
beforeEach(() => {
  jest.clearAllMocks();
  if (global.wx) {
    Object.values(global.wx).forEach(mock => {
      if (typeof mock === 'function' && mock.mockClear) {
        mock.mockClear();
      }
    });
  }
});

// Clean up after all tests
afterAll(() => {
  jest.resetAllMocks();
});