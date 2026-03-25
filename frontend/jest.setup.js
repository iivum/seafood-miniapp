/**
 * Jest setup file for mini-program testing
 * Provides mocks for wx API and other mini-program globals
 */

// Mock the wx global object for mini-program testing
global.wx = {
  request: jest.fn(),
  showToast: jest.fn(),
  removeStorageSync: jest.fn(),
  reLaunch: jest.fn(),
  getSystemInfoSync: jest.fn(() => ({
    SDKVersion: '3.0.0',
    version: '8.0.0',
    brand: 'devtools',
    model: 'iPhone 14',
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