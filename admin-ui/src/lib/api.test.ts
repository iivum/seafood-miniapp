import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readCookie, writeCookie } from './api';

vi.mock('axios', () => {
  const post = vi.fn();
  const create = vi.fn(() => ({
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
    post,
  }));
  return { default: { create, post }, post };
});

describe('cookie helpers', () => {
  beforeEach(() => {
    document.cookie = 'admin_refresh_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  it('round-trips a value', () => {
    writeCookie('admin_refresh_token', 'rt-xyz', 60);
    expect(readCookie('admin_refresh_token')).toBe('rt-xyz');
  });

  it('clears a value with maxAge=0', () => {
    writeCookie('admin_refresh_token', 'rt', 60);
    writeCookie('admin_refresh_token', '', 0);
    expect(readCookie('admin_refresh_token')).toBe(null);
  });
});
