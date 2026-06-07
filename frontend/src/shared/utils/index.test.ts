import { formatYuan, formatDate } from './index';

describe('shared/utils', () => {
  describe('formatYuan', () => {
    it('formats whole numbers with 2 decimals', () => {
      expect(formatYuan(10)).toBe('¥ 10.00');
    });
    it('formats decimals', () => {
      expect(formatYuan(10.5)).toBe('¥ 10.50');
    });
    it('handles 0', () => {
      expect(formatYuan(0)).toBe('¥ 0.00');
    });
    it('handles NaN', () => {
      expect(formatYuan(Number.NaN)).toBe('¥ 0.00');
    });
  });

  describe('formatDate', () => {
    it('formats ISO string to yyyy-mm-dd hh:mm', () => {
      const iso = '2026-06-05T10:30:00Z';
      // The function uses local time, so the assertion uses a regex
      const out = formatDate(iso);
      expect(out).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/);
    });
    it('returns empty for empty input', () => {
      expect(formatDate('')).toBe('');
    });
    it('returns the original string for invalid input', () => {
      expect(formatDate('not-a-date')).toBe('not-a-date');
    });
  });
});
