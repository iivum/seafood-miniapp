import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

// jsdom doesn't ship localStorage by default in all versions; provide a minimal shim
const memoryStore = new Map<string, string>();
const localStorageMock: Storage = {
  get length() {
    return memoryStore.size;
  },
  clear: () => memoryStore.clear(),
  getItem: (key: string) => (memoryStore.has(key) ? (memoryStore.get(key) as string) : null),
  key: (i: number) => Array.from(memoryStore.keys())[i] ?? null,
  removeItem: (key: string) => {
    memoryStore.delete(key);
  },
  setItem: (key: string, value: string) => {
    memoryStore.set(key, String(value));
  },
};
/* eslint-disable no-restricted-globals */
const g = globalThis as unknown as { localStorage?: Storage };
if (typeof g.localStorage === 'undefined' || !g.localStorage.clear) {
  Object.defineProperty(g, 'localStorage', { configurable: true, value: localStorageMock });
}

beforeEach(() => {
  memoryStore.clear();
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

// Provide a minimal `matchMedia` shim (some Radix components call it)
const w = (typeof globalThis !== 'undefined' ? globalThis : undefined) as unknown as {
  matchMedia?: (query: string) => MediaQueryList;
} | undefined;
if (w && !w.matchMedia) {
  Object.defineProperty(w, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}
/* eslint-enable no-restricted-globals */

// Recharts ResponsiveContainer relies on ResizeObserver(jsdom 未实现)。
// 给一个 noop polyfill,避免测试中 chart 抛 ReferenceError。
const w2 = (typeof globalThis !== 'undefined' ? globalThis : undefined) as unknown as {
  ResizeObserver?: new (cb: ResizeObserverCallback) => ResizeObserver;
} | undefined;
if (w2 && !w2.ResizeObserver) {
  class RO {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  Object.defineProperty(w2, 'ResizeObserver', { writable: true, value: RO });
}
