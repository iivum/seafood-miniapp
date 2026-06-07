#!/usr/bin/env node
/**
 * scripts/generate-wxss-tokens.js
 *
 * Reads the canonical `frontend/admin-design/tokens.json` (shared with
 * the admin-ui) and emits the `:root` block of CSS variables for the
 * mini-program. The output is appended to `frontend/app.wxss` between
 * the sentinel comments:
 *
 *   /* === TOKENS:BEGIN === *\/
 *   /* === TOKENS:END   === *\/
 *
 * The admin-ui reads the same JSON via Tailwind config, so updating
 * `tokens.json` regenerates both surfaces.
 *
 * Usage:
 *   node scripts/generate-wxss-tokens.js           # writes app.wxss
 *   node scripts/generate-wxss-tokens.js --check   # exits 1 if drift
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const TOKENS_JSON = path.join(ROOT, 'admin-design', 'tokens.json');
const APP_WXSS = path.join(ROOT, 'app.wxss');

const BEGIN = '/* === TOKENS:BEGIN === */';
const END = '/* === TOKENS:END   === */';

function readTokens() {
  const raw = fs.readFileSync(TOKENS_JSON, 'utf8');
  return JSON.parse(raw);
}

function resolveWxssTokens(t) {
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

    /* spacing */
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

function renderBlock(map) {
  const lines = Object.keys(map)
    .sort()
    .map((k) => `  ${k}: ${map[k]};`);
  return `page {\n${lines.join('\n')}\n}\n`;
}

function buildBlock() {
  const tokens = readTokens();
  const map = resolveWxssTokens(tokens);
  return `${BEGIN}\n${renderBlock(map)}${END}\n`;
}

function main() {
  const args = new Set(process.argv.slice(2));
  const checkMode = args.has('--check');

  const block = buildBlock();
  const wxss = fs.readFileSync(APP_WXSS, 'utf8');
  const startIdx = wxss.indexOf(BEGIN);
  const endIdx = wxss.indexOf(END);

  if (checkMode) {
    if (startIdx === -1 || endIdx === -1) {
      console.error('tokens: sentinels not found in app.wxss — run without --check first');
      process.exit(1);
    }
    const before = wxss.slice(0, startIdx + BEGIN.length);
    const after = wxss.slice(endIdx);
    const current = wxss.slice(startIdx + BEGIN.length, endIdx);
    const expected = '\n' + renderBlock(resolveWxssTokens(readTokens())) + END;
    if (current + END !== expected.replace(END, '') + END) {
      console.error('tokens: app.wxss is out of sync with admin-design/tokens.json');
      process.exit(1);
    }
    void before;
    void after;
    console.log('tokens: app.wxss matches tokens.json');
    return;
  }

  let next;
  if (startIdx === -1 || endIdx === -1) {
    // First run — append the block at the end of the file.
    next = wxss.replace(/\s*$/, '') + '\n\n' + block;
  } else {
    const before = wxss.slice(0, startIdx);
    const after = wxss.slice(endIdx + END.length);
    next = before + block + after;
  }

  fs.writeFileSync(APP_WXSS, next);
  console.log('tokens: regenerated app.wxss from admin-design/tokens.json');
}

main();
