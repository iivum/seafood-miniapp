#!/usr/bin/env node
/**
 * Build the v2 design tokens (single source: docs/redesign/tokens.json) into
 * the two consuming surfaces:
 *
 *   - frontend/src/shared/tokens/tokens.wxss   (WeChat mini-program, CSS vars)
 *   - admin-ui/src/shared/tokens/tokens.tailwind.ts  (admin-ui Tailwind theme)
 *
 * See openspec/changes/v2-visual-redesign/specs/visual-design-system/ for the
 * full normative requirements this script satisfies. Two relevant ones:
 *
 *   - "Token JSON rejected on missing required key" — we read 15+ required
 *     color keys; missing any → exit 1.
 *   - "Token JSON rejects hex values" — every color string MUST start with
 *     `oklch(`. Hex `#ff0000` or `rgb(...)` → exit 1.
 *
 * The v1 design system in `frontend/admin-design/tokens.json` remains the
 * production source until design owner signs off on v2 (see proposal § "已落定
 * 决策" — design owner must verify before stream switch).
 *
 * Usage:
 *   node scripts/build-tokens.js
 *   npm run build:tokens
 *
 * Exit codes:
 *   0  built successfully
 *   1  validation failure (missing key, hex color, JSON syntax error)
 *   2  file I/O failure
 */

'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const INPUT = path.join(ROOT, 'docs/redesign/tokens.json');
const OUT_MP = path.join(ROOT, 'frontend/src/shared/tokens/tokens.wxss');
const OUT_ADMIN = path.join(ROOT, 'admin-ui/src/shared/tokens/tokens.tailwind.ts');

// ---------------------------------------------------------------------------
// Required color keys — see visual-design-system spec scenario "Token JSON
// present and well-formed".
// ---------------------------------------------------------------------------
const REQUIRED_COLOR_KEYS = [
  'bg', 'surface', 'fg', 'muted', 'soft',
  'border', 'border-strong',
  'accent', 'accent-soft', 'accent-strong', 'accent-deep',
  'success', 'warning', 'error', 'info',
];
const REQUIRED_STATE_SOFT_KEYS = [
  'success-soft', 'warning-soft', 'error-soft', 'info-soft',
];
const REQUIRED_TYPOGRAPHY_KEYS = ['display', 'body', 'mono'];
const REQUIRED_RADIUS_KEYS = ['sm', 'md', 'lg', 'xl', '2xl', '3xl', 'pill'];
const REQUIRED_SHADOW_KEYS = ['sm', 'md', 'lg'];

// ---------------------------------------------------------------------------
// Validation
// ---------------------------------------------------------------------------
function fail(message) {
  console.error('build-tokens: ' + message);
  process.exit(1);
}

function validateColors(colors) {
  if (!colors || typeof colors !== 'object') fail('tokens.colors is missing or not an object');

  for (const key of REQUIRED_COLOR_KEYS) {
    if (!(key in colors)) {
      fail('missing required color key: colors.' + key);
    }
  }

  for (const [key, value] of Object.entries(colors)) {
    // shell / state-soft / _meta / postures are sub-objects, not single colors
    if (key === 'state-soft' || key === 'shell') continue;
    if (key.startsWith('_')) continue;

    if (typeof value !== 'string') {
      fail('color value at colors.' + key + ' must be a string, got ' + typeof value);
    }
    if (!/^oklch\(/.test(value)) {
      fail('all color values MUST be oklch(...), got: ' + value + ' at colors.' + key);
    }
  }

  if (!colors['state-soft'] || typeof colors['state-soft'] !== 'object') {
    fail('colors.state-soft is missing');
  }
  for (const key of REQUIRED_STATE_SOFT_KEYS) {
    if (!(key in colors['state-soft'])) {
      fail('missing required state-soft key: colors.state-soft.' + key);
    }
    if (!/^oklch\(/.test(colors['state-soft'][key])) {
      fail('colors.state-soft.' + key + ' must be oklch(...), got: ' + colors['state-soft'][key]);
    }
  }
}

function validateTypography(typo) {
  if (!typo || typeof typo !== 'object') fail('tokens.typography is missing');
  for (const key of REQUIRED_TYPOGRAPHY_KEYS) {
    if (!(key in typo)) fail('missing required typography key: typography.' + key);
    if (typeof typo[key] !== 'string' || typo[key].length === 0) {
      fail('typography.' + key + ' must be a non-empty string');
    }
  }
}

function validateRadius(radius) {
  if (!radius || typeof radius !== 'object') fail('tokens.radius is missing');
  for (const key of REQUIRED_RADIUS_KEYS) {
    if (!(key in radius)) fail('missing required radius key: radius.' + key);
  }
}

function validateShadow(shadow) {
  if (!shadow || typeof shadow !== 'object') fail('tokens.shadow is missing');
  for (const key of REQUIRED_SHADOW_KEYS) {
    if (!(key in shadow)) fail('missing required shadow key: shadow.' + key);
  }
}

// ---------------------------------------------------------------------------
// Build mp tokens.wxss — emits a single :root { ... } block with CSS custom
// properties. WXSS supports the same var() syntax as CSS, including
// `oklch(...)` values (微信小程序 8.0+ WebView support).
// ---------------------------------------------------------------------------
function buildWxss(tokens) {
  const lines = [
    '/*',
    ' * AUTO-GENERATED from docs/redesign/tokens.json by scripts/build-tokens.js',
    ' * DO NOT EDIT — edit the JSON source and re-run: npm run build:tokens',
    ' *',
    ' * See openspec/changes/v2-visual-redesign/specs/visual-design-system/',
    ' */',
    ':root {',
  ];

  // Colors (flat + state-soft flattened as --state-<name>-soft)
  for (const key of REQUIRED_COLOR_KEYS) {
    lines.push('  --' + key + ': ' + tokens.colors[key] + ';');
  }
  for (const key of REQUIRED_STATE_SOFT_KEYS) {
    lines.push('  --' + key + ': ' + tokens.colors['state-soft'][key] + ';');
  }

  // Radii
  for (const key of REQUIRED_RADIUS_KEYS) {
    lines.push('  --radius-' + key + ': ' + tokens.radius[key] + ';');
  }

  // Shadows
  for (const key of REQUIRED_SHADOW_KEYS) {
    lines.push('  --shadow-' + key + ': ' + tokens.shadow[key] + ';');
  }

  // Typography — emit as --font-<name>
  for (const key of REQUIRED_TYPOGRAPHY_KEYS) {
    lines.push('  --font-' + key + ': ' + JSON.stringify(tokens.typography[key]) + ';');
  }

  lines.push('}');
  lines.push(''); // trailing newline
  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// Build admin-ui tokens.tailwind.ts — typed const object. admin-ui consumers
// import this in tailwind.config.ts to drive theme.extend.{colors, fontFamily,
// borderRadius, boxShadow}.  See admin-ui spec § "Admin UI consumes OKLch
// token system".
// ---------------------------------------------------------------------------
function buildAdminTokens(tokens) {
  // Strip the _meta + state-soft sub-object + shell + postures for the colors
  // payload; those are mp-specific or non-consumable. The admin Tailwind theme
  // wants flat key → oklch value.
  const flatColors = {};
  for (const key of REQUIRED_COLOR_KEYS) {
    flatColors[key] = tokens.colors[key];
  }
  for (const key of REQUIRED_STATE_SOFT_KEYS) {
    flatColors[key] = tokens.colors['state-soft'][key];
  }

  return [
    '/**',
    ' * AUTO-GENERATED from docs/redesign/tokens.json by scripts/build-tokens.js',
    ' * DO NOT EDIT — edit the JSON source and re-run: npm run build:tokens',
    ' *',
    ' * Consumed by admin-ui/tailwind.config.ts to drive theme.extend.{colors,',
    ' * fontFamily, borderRadius, boxShadow}.',
    ' */',
    '',
    'export const tokens = ' + JSON.stringify(flatColors, null, 2) + ' as const;',
    '',
    'export const typography = ' + JSON.stringify(tokens.typography, null, 2) + ' as const;',
    '',
    'export const radius = ' + JSON.stringify(tokens.radius, null, 2) + ' as const;',
    '',
    'export const shadow = ' + JSON.stringify(tokens.shadow, null, 2) + ' as const;',
    '',
  ].join('\n');
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------
function main() {
  if (!fs.existsSync(INPUT)) {
    fail('input file not found: ' + INPUT);
  }

  let tokens;
  try {
    tokens = JSON.parse(fs.readFileSync(INPUT, 'utf-8'));
  } catch (e) {
    fail('JSON parse error in ' + INPUT + ': ' + e.message);
  }

  validateColors(tokens.colors);
  validateTypography(tokens.typography);
  validateRadius(tokens.radius);
  validateShadow(tokens.shadow);

  const wxss = buildWxss(tokens);
  const adminTs = buildAdminTokens(tokens);

  try {
    fs.mkdirSync(path.dirname(OUT_MP), { recursive: true });
    fs.mkdirSync(path.dirname(OUT_ADMIN), { recursive: true });
    fs.writeFileSync(OUT_MP, wxss, 'utf-8');
    fs.writeFileSync(OUT_ADMIN, adminTs, 'utf-8');
  } catch (e) {
    console.error('build-tokens: I/O failure: ' + e.message);
    process.exit(2);
  }

  console.log(
    'build-tokens: OK\n' +
      '  → ' + path.relative(ROOT, OUT_MP) + ' (' + wxss.length + ' bytes)\n' +
      '  → ' + path.relative(ROOT, OUT_ADMIN) + ' (' + adminTs.length + ' bytes)'
  );
}

main();
