/**
 * AUTO-GENERATED from docs/redesign/tokens.json by scripts/build-tokens.js
 * DO NOT EDIT — edit the JSON source and re-run: npm run build:tokens
 *
 * Consumed by admin-ui/tailwind.config.ts to drive theme.extend.{colors,
 * fontFamily, borderRadius, boxShadow}.
 */

export const tokens = {
  "bg": "oklch(99% 0.006 60)",
  "surface": "oklch(100% 0 0)",
  "fg": "oklch(22% 0.02 40)",
  "muted": "oklch(50% 0.015 40)",
  "soft": "oklch(70% 0.012 40)",
  "border": "oklch(91% 0.008 40)",
  "border-strong": "oklch(85% 0.012 40)",
  "accent": "oklch(64% 0.16 38)",
  "accent-soft": "oklch(96% 0.05 40)",
  "accent-strong": "oklch(52% 0.18 40)",
  "accent-deep": "oklch(35% 0.10 38)",
  "success": "oklch(58% 0.12 155)",
  "warning": "oklch(72% 0.15 70)",
  "error": "oklch(50% 0.20 15)",
  "info": "oklch(58% 0.10 220)",
  "success-soft": "oklch(95% 0.05 155)",
  "warning-soft": "oklch(95% 0.05 70)",
  "error-soft": "oklch(95% 0.05 15)",
  "info-soft": "oklch(95% 0.04 220)"
} as const;

export const typography = {
  "display": "Fraunces, 'Source Serif Pro', 'Iowan Old Style', Georgia, serif",
  "body": "Inter Tight, -apple-system, BlinkMacSystemFont, 'SF Pro Text', system-ui, sans-serif",
  "mono": "Geist Mono, 'IBM Plex Mono', ui-monospace, Menlo, monospace"
} as const;

export const radius = {
  "22": "22px",
  "28": "28px",
  "sm": "4px",
  "md": "6px",
  "lg": "10px",
  "xl": "14px",
  "2xl": "16px",
  "3xl": "18px",
  "pill": "9999px"
} as const;

export const shadow = {
  "sm": "0 1px 2px oklch(22% 0.02 40 / 0.05)",
  "md": "0 4px 14px oklch(22% 0.02 40 / 0.07)",
  "lg": "0 24px 60px oklch(22% 0.02 40 / 0.10)"
} as const;
