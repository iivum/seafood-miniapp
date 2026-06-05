import { tokens, resolveWxssTokens, type DesignTokens } from './index';

describe('shared/tokens', () => {
  it('parses tokens.json into a typed object', () => {
    expect(tokens.color.accent.coral['500']).toMatch(/^#[0-9A-Fa-f]{6}$/);
    expect(tokens.font.size.body).toBeDefined();
    expect(tokens.spacing['16']).toBeDefined();
  });

  it('exposes a DesignTokens type (compile-time)', () => {
    const t: DesignTokens = tokens;
    expect(t.color.primary['500']).toBeDefined();
  });

  describe('resolveWxssTokens', () => {
    it('returns CSS-variable names that start with --', () => {
      const map = resolveWxssTokens();
      Object.keys(map).forEach((k) => {
        expect(k.startsWith('--')).toBe(true);
      });
    });

    it('maps the canonical color tokens', () => {
      const map = resolveWxssTokens();
      expect(map['--color-primary']).toBe(tokens.color.accent.coral['500']);
      expect(map['--color-secondary']).toBe(tokens.color.accent.teal['400']);
      expect(map['--color-success']).toBe(tokens.color.feedback.success);
      expect(map['--color-error']).toBe(tokens.color.feedback.error);
    });

    it('maps spacing and radius', () => {
      const map = resolveWxssTokens();
      expect(map['--space-16']).toBe(tokens.spacing['16']);
      expect(map['--radius-full']).toBe(tokens.radius.full);
    });

    it('maps font sizes and z-indexes', () => {
      const map = resolveWxssTokens();
      expect(map['--font-size-body']).toBe(tokens.font.size.body);
      expect(map['--z-toast']).toBe(String(tokens.zIndex.toast));
    });
  });
});
