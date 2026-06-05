/**
 * Shared design tokens.
 *
 * The canonical source of truth is `frontend/admin-design/tokens.json`
 * (shared with the admin-ui, see design.md §7.1). At build time the
 * `scripts/generate-wxss-tokens.js` script reads that JSON and emits
 * a WXSS file (or the `:root` block of `app.wxss`) containing the
 * `var(--color-*)` / `var(--space-*)` variables. The same JSON is
 * also re-exported here so TypeScript code can access token values
 * directly (e.g. for programmatic style use).
 *
 * CI syncs `tokens.json` between `frontend/` and `admin-ui/` so the
 * two surfaces stay in lockstep.
 */

import tokensJson from '../../../admin-design/tokens.json';

export interface ColorScale {
  '50': string;
  '100': string;
  '200': string;
  '300': string;
  '400': string;
  '500': string;
  '600': string;
  '700': string;
  '800': string;
  '900': string;
}

export interface CoralScale {
  '50': string;
  '100': string;
  '200': string;
  '300': string;
  '400': string;
  '500': string;
  '600': string;
}

export interface TealScale {
  '50': string;
  '100': string;
  '200': string;
  '300': string;
  '400': string;
  '500': string;
  '600': string;
}

export interface SidebarTokens {
  bg: string;
  text: string;
  textMuted: string;
  hover: string;
  active: string;
}

export interface ContentTokens {
  bg: string;
  surface: string;
  text: string;
  textMuted: string;
  border: string;
  divider: string;
}

export interface FeedbackTokens {
  success: string;
  warning: string;
  error: string;
  info: string;
}

export interface ColorTokens {
  primary: ColorScale;
  accent: { coral: CoralScale; teal: TealScale };
  sidebar: SidebarTokens;
  content: ContentTokens;
  feedback: FeedbackTokens;
}

export interface FontFamily {
  display: string;
  body: string;
  mono: string;
}

export interface FontSize {
  display: string;
  h1: string;
  h2: string;
  h3: string;
  body: string;
  small: string;
  xs: string;
}

export interface FontWeight {
  regular: number;
  medium: number;
  semibold: number;
  bold: number;
}

export interface LineHeight {
  tight: number;
  normal: number;
  relaxed: number;
}

export interface FontTokens {
  family: FontFamily;
  size: FontSize;
  weight: FontWeight;
  lineHeight: LineHeight;
}

export interface SpacingTokens {
  '4': string;
  '8': string;
  '12': string;
  '16': string;
  '20': string;
  '24': string;
  '32': string;
  '40': string;
  '48': string;
  '64': string;
  '80': string;
}

export interface RadiusTokens {
  sm: string;
  md: string;
  lg: string;
  xl: string;
  '2xl': string;
  full: string;
}

export interface ShadowTokens {
  sm: string;
  md: string;
  lg: string;
  xl: string;
}

export interface TransitionTokens {
  fast: string;
  normal: string;
  slow: string;
}

export interface BreakpointTokens {
  sm: string;
  md: string;
  lg: string;
  xl: string;
  '2xl': string;
}

export interface ZIndexTokens {
  sidebar: number;
  header: number;
  dropdown: number;
  modal: number;
  toast: number;
}

export interface DesignTokens {
  color: ColorTokens;
  font: FontTokens;
  spacing: SpacingTokens;
  radius: RadiusTokens;
  shadow: ShadowTokens;
  transition: TransitionTokens;
  breakpoint: BreakpointTokens;
  zIndex: ZIndexTokens;
}

export const tokens = tokensJson as DesignTokens;

/**
 * Map a subset of design tokens to the CSS variable names consumed
 * by WXSS. The mapping lives in this module (not generated) so that
 * the WXSS-side names are stable and don't need to be regenerated
 * each time the JSON gains a new token.
 */
export interface WxssTokenMap {
  /** CSS var name → resolved value from tokens.json */
  readonly [cssVar: string]: string;
}

/**
 * Resolve the WXSS-side tokens that the mini-program's WXSS files
 * actually reference. New tokens added to `tokens.json` will not
 * show up in WXSS until they are added here — this is intentional
 * so the WXSS surface stays curated.
 */
export function resolveWxssTokens(): WxssTokenMap {
  const t = tokens;
  return {
    /* color */
    '--color-primary': t.color.accent.coral['500'],
    '--color-primary-soft': t.color.accent.coral['100'],
    '--color-secondary': t.color.accent.teal['400'],
    '--color-accent': t.color.accent.coral['300'],
    '--color-dark': t.color.primary['700'],
    '--color-surface': t.color.content.surface,
    '--color-bg': t.color.content.bg,
    '--color-bg-subtle': t.color.content.divider,
    '--color-text': t.color.content.text,
    '--color-text-secondary': t.color.content.textMuted,
    '--color-text-muted': t.color.content.textMuted,
    '--color-border': t.color.content.border,
    '--color-divider': t.color.content.divider,
    '--color-price': t.color.accent.coral['500'],
    '--color-success': t.color.feedback.success,
    '--color-warning': t.color.feedback.warning,
    '--color-error': t.color.feedback.error,
    '--color-info': t.color.feedback.info,

    /* spacing (4 / 8 / 12 / 16 / 20 / 24 / 32) */
    '--space-4': t.spacing['4'],
    '--space-8': t.spacing['8'],
    '--space-12': t.spacing['12'],
    '--space-16': t.spacing['16'],
    '--space-20': t.spacing['20'],
    '--space-24': t.spacing['24'],
    '--space-32': t.spacing['32'],

    /* radius */
    '--radius-sm': t.radius.sm,
    '--radius-md': t.radius.md,
    '--radius-lg': t.radius.lg,
    '--radius-xl': t.radius.xl,
    '--radius-2xl': t.radius['2xl'],
    '--radius-full': t.radius.full,

    /* font sizes */
    '--font-size-display': t.font.size.display,
    '--font-size-h1': t.font.size.h1,
    '--font-size-h2': t.font.size.h2,
    '--font-size-h3': t.font.size.h3,
    '--font-size-body': t.font.size.body,
    '--font-size-small': t.font.size.small,
    '--font-size-xs': t.font.size.xs,

    /* z-index */
    '--z-toast': String(t.zIndex.toast),
    '--z-modal': String(t.zIndex.modal),
    '--z-dropdown': String(t.zIndex.dropdown),
  };
}
