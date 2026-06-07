import { describe, it, expect } from 'vitest';
import { cn, formatDateTime, formatPrice } from './utils';

describe('cn', () => {
  it('merges and de-duplicates Tailwind classes', () => {
    expect(cn('px-4', 'px-2')).toBe('px-2');
    expect(cn('text-red-500', false && 'text-blue-500', 'text-green-500')).toBe('text-green-500');
  });
});

describe('formatPrice', () => {
  it('formats numbers and numeric strings as CNY', () => {
    expect(formatPrice(12.5)).toBe('¥12.50');
    expect(formatPrice('88')).toBe('¥88.00');
  });
  it('returns em-dash for null/undefined', () => {
    expect(formatPrice(null)).toBe('—');
    expect(formatPrice(undefined)).toBe('—');
  });
});

describe('formatDateTime', () => {
  it('returns em-dash for null/undefined', () => {
    expect(formatDateTime(null)).toBe('—');
  });
  it('formats an ISO date', () => {
    const out = formatDateTime('2026-01-15T08:30:00Z');
    expect(out).toMatch(/2026/);
  });
});
