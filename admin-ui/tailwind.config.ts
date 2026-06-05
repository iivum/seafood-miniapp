import type { Config } from 'tailwindcss';
import animate from 'tailwindcss-animate';
import tokens from '../frontend/admin-design/tokens.json';

/**
 * All design tokens (color, spacing, typography, radius, shadow, breakpoint)
 * come from `frontend/admin-design/tokens.json` so admin-ui and the mini-program
 * share the same visual language. See openspec design §7.2 + §8 visual baseline.
 */
const color = (path: string) =>
  path
    .split('.')
    .reduce<unknown>((acc, key) => (acc as Record<string, unknown>)[key], tokens);

type Tokens = typeof tokens;
type ColorTokens = Tokens['color'];

function flattenColorTree(node: ColorTokens, prefix = ''): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [key, value] of Object.entries(node)) {
    const next = prefix ? `${prefix}-${key}` : key;
    if (typeof value === 'string') {
      out[next] = value;
    } else {
      Object.assign(out, flattenColorTree(value as ColorTokens, next));
    }
  }
  return out;
}

const primaryPalette = color('color.primary') as Record<string, string>;
const coralPalette = color('color.accent.coral') as Record<string, string>;
const tealPalette = color('color.accent.teal') as Record<string, string>;

const brandColors = {
  ...Object.fromEntries(Object.entries(primaryPalette).map(([k, v]) => [`primary-${k}`, v])),
  ...Object.fromEntries(Object.entries(coralPalette).map(([k, v]) => [`coral-${k}`, v])),
  ...Object.fromEntries(Object.entries(tealPalette).map(([k, v]) => [`teal-${k}`, v])),
};

const semanticColors = {
  sidebar: {
    DEFAULT: color('color.sidebar.bg') as string,
    text: color('color.sidebar.text') as string,
    muted: color('color.sidebar.textMuted') as string,
    hover: color('color.sidebar.hover') as string,
    active: color('color.sidebar.active') as string,
  },
  app: {
    bg: color('color.content.bg') as string,
    surface: color('color.content.surface') as string,
    text: color('color.content.text') as string,
    muted: color('color.content.textMuted') as string,
    border: color('color.content.border') as string,
    divider: color('color.content.divider') as string,
  },
  success: color('color.feedback.success') as string,
  warning: color('color.feedback.warning') as string,
  'feedback-error': color('color.feedback.error') as string,
  info: color('color.feedback.info') as string,
};

const config: Config = {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ...brandColors,
        ...semanticColors,
        ring: primaryPalette['500'],
        background: semanticColors.app.bg,
        foreground: semanticColors.app.text,
        primary: {
          DEFAULT: primaryPalette['500'],
          foreground: '#FFFFFF',
        },
        secondary: {
          DEFAULT: semanticColors.app.surface,
          foreground: semanticColors.app.text,
        },
        muted: {
          DEFAULT: semanticColors.app.divider,
          foreground: semanticColors.app.muted,
        },
        accent: {
          DEFAULT: coralPalette['400'],
          foreground: '#FFFFFF',
        },
        destructive: {
          DEFAULT: color('color.feedback.error') as string,
          foreground: '#FFFFFF',
        },
        card: {
          DEFAULT: semanticColors.app.surface,
          foreground: semanticColors.app.text,
        },
      },
      fontFamily: {
        sans: [color('font.family.body') as string],
        display: [color('font.family.display') as string],
        mono: [color('font.family.mono') as string],
      },
      fontSize: {
        display: [color('font.size.display') as string, { lineHeight: color('font.lineHeight.tight') as string }],
        h1: [color('font.size.h1') as string, { lineHeight: color('font.lineHeight.tight') as string }],
        h2: [color('font.size.h2') as string, { lineHeight: color('font.lineHeight.tight') as string }],
        h3: [color('font.size.h3') as string, { lineHeight: color('font.lineHeight.normal') as string }],
        body: [color('font.size.body') as string, { lineHeight: color('font.lineHeight.normal') as string }],
        small: [color('font.size.small') as string, { lineHeight: color('font.lineHeight.normal') as string }],
        xs: [color('font.size.xs') as string, { lineHeight: color('font.lineHeight.normal') as string }],
      },
      fontWeight: {
        regular: color('font.weight.regular') as unknown as number,
        medium: color('font.weight.medium') as unknown as number,
        semibold: color('font.weight.semibold') as unknown as number,
        bold: color('font.weight.bold') as unknown as number,
      },
      borderRadius: {
        none: '0',
        sm: color('radius.sm') as string,
        md: color('radius.md') as string,
        lg: color('radius.lg') as string,
        xl: color('radius.xl') as string,
        '2xl': color('radius.2xl') as string,
        full: color('radius.full') as string,
      },
      boxShadow: {
        sm: color('shadow.sm') as string,
        md: color('shadow.md') as string,
        lg: color('shadow.lg') as string,
        xl: color('shadow.xl') as string,
      },
      transitionDuration: {
        fast: '150ms',
        normal: '250ms',
        slow: '350ms',
      },
      spacing: Object.fromEntries(
        Object.entries(tokens.spacing as Record<string, string>).map(([k, v]) => [k, v]),
      ),
      zIndex: {
        sidebar: color('zIndex.sidebar') as unknown as number,
        header: color('zIndex.header') as unknown as number,
        dropdown: color('zIndex.dropdown') as unknown as number,
        modal: color('zIndex.modal') as unknown as number,
        toast: color('zIndex.toast') as unknown as number,
      },
    },
  },
  plugins: [animate],
};

export default config;
// re-export for tests that want to assert token-driven config shape
export { flattenColorTree };
