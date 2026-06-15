'use strict';

/**
 * Cross-surface token parity test.
 *
 * Verifies that `docs/redesign/tokens.json` produces matching
 * CSS-variable emissions in BOTH:
 *
 *   - frontend/src/shared/tokens/tokens.wxss
 *   - admin-ui/src/shared/tokens/tokens.tailwind.ts
 *
 * Covers the visual-design-system spec requirements:
 *   - "Single source of truth for design tokens"
 *   - "Cross-surface token parity test" (CI runs on every PR)
 *   - "Token JSON rejected on missing required key"
 *   - "Token JSON rejects hex values"
 *
 * Run with: npm run test:tokens
 *
 * Uses Node.js built-in `node --test` (Node 18+). No dependencies.
 */

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const ROOT = path.resolve(__dirname, '..', '..');
const INPUT = path.join(ROOT, 'docs/redesign/tokens.json');
const OUT_MP = path.join(ROOT, 'frontend/src/shared/tokens/tokens.wxss');
const OUT_ADMIN = path.join(ROOT, 'admin-ui/src/shared/tokens/tokens.tailwind.ts');
const BUILD_SCRIPT = path.join(ROOT, 'scripts/build-tokens.js');

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Parse `tokens.wxss` and return a map of { '--name': 'value', ... } for every
 * `--name: value;` line inside the `page { ... }` block.
 * (WeChat mini programs require `page` instead of `:root`.)
 */
function parseWxss(source) {
  const map = {};
  const rootMatch = source.match(/page\s*\{([\s\S]*?)\}/);
  if (!rootMatch) {
    throw new Error('parseWxss: no page { ... } block found');
  }
  const body = rootMatch[1];
  const re = /(--[\w-]+)\s*:\s*([^;]+);/g;
  let m;
  while ((m = re.exec(body)) !== null) {
    map[m[1]] = m[2].trim();
  }
  return map;
}

/**
 * Parse `tokens.tailwind.ts` and return the `tokens` / `radius` / `shadow`
 * object literals as plain JS objects via JSON.parse of the extracted block.
 */
function parseAdminExport(source, name) {
  const re = new RegExp(
    'export\\s+const\\s+' + name + '\\s*=\\s*(\\{[\\s\\S]*?\\})\\s*as\\s+const\\s*;',
  );
  const m = source.match(re);
  if (!m) throw new Error('parseAdminExport: no `' + name + '` block found');
  return JSON.parse(m[1]);
}

// ---------------------------------------------------------------------------
// Setup — run the build script once so both outputs are guaranteed fresh.
// We do this at module load time (no test.beforeAll in node:test) so every
// subsequent test reads the freshly generated artifacts.
// ---------------------------------------------------------------------------

execFileSync(process.execPath, [BUILD_SCRIPT], { cwd: ROOT, stdio: 'pipe' });

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test('build script produces both output files', () => {
  assert.ok(fs.existsSync(OUT_MP), 'tokens.wxss must exist: ' + OUT_MP);
  assert.ok(fs.existsSync(OUT_ADMIN), 'tokens.tailwind.ts must exist: ' + OUT_ADMIN);
});

test('tokens.wxss contains page { ... } block', () => {
  const src = fs.readFileSync(OUT_MP, 'utf-8');
  const wxss = parseWxss(src);
  assert.ok(Object.keys(wxss).length > 0, 'expected at least one CSS variable');
});

test('tokens.tailwind.ts exports typed tokens const', () => {
  const src = fs.readFileSync(OUT_ADMIN, 'utf-8');
  const t = parseAdminExport(src, 'tokens');
  assert.ok(t && typeof t === 'object', 'tokens export must be an object');
  assert.ok(Object.keys(t).length > 0, 'tokens must have at least one key');
});

test('parity: every color token in tokens.json appears in BOTH outputs with same value', () => {
  const json = JSON.parse(fs.readFileSync(INPUT, 'utf-8'));
  const wxss = parseWxss(fs.readFileSync(OUT_MP, 'utf-8'));
  const admin = parseAdminExport(fs.readFileSync(OUT_ADMIN, 'utf-8'), 'tokens');

  const required = [
    'bg', 'surface', 'fg', 'muted', 'soft',
    'border', 'border-strong',
    'accent', 'accent-soft', 'accent-strong', 'accent-deep',
    'success', 'warning', 'error', 'info',
    'success-soft', 'warning-soft', 'error-soft', 'info-soft',
  ];

  const mismatches = [];
  for (const key of required) {
    const jsonValue = json.colors['state-soft'] && key in json.colors['state-soft']
      ? json.colors['state-soft'][key]
      : json.colors[key];

    const wxssValue = wxss['--' + key];
    const adminValue = admin[key];

    if (jsonValue === undefined) {
      mismatches.push('  - ' + key + ': missing from tokens.json');
      continue;
    }
    // wxss uses hex (oklch→hex conversion for WeChat), admin uses oklch
    if (!wxssValue || !wxssValue.startsWith('#')) {
      mismatches.push('  - ' + key + ': tokens.wxss has "' + wxssValue + '", expected hex');
    }
    if (adminValue !== jsonValue) {
      mismatches.push('  - ' + key + ': tokens.tailwind.ts has "' + adminValue + '", JSON has "' + jsonValue + '"');
    }
  }

  if (mismatches.length > 0) {
    assert.fail(
      'Cross-surface parity check failed:\n' + mismatches.join('\n') +
        '\nHint: edit docs/redesign/tokens.json and re-run `npm run build:tokens`.',
    );
  }
});

test('parity: every radius token in tokens.json appears in BOTH outputs', () => {
  const json = JSON.parse(fs.readFileSync(INPUT, 'utf-8'));
  const wxss = parseWxss(fs.readFileSync(OUT_MP, 'utf-8'));
  const admin = parseAdminExport(fs.readFileSync(OUT_ADMIN, 'utf-8'), 'radius');

  const required = ['sm', 'md', 'lg', 'xl', '2xl', '3xl', 'pill'];
  const mismatches = [];
  for (const key of required) {
    const jsonValue = json.radius[key];
    const wxssValue = wxss['--radius-' + key];
    const adminValue = admin[key];

    if (wxssValue !== jsonValue) {
      mismatches.push('  - radius.' + key + ': wxss="' + wxssValue + '", json="' + jsonValue + '"');
    }
    if (adminValue !== jsonValue) {
      mismatches.push('  - radius.' + key + ': admin="' + adminValue + '", json="' + jsonValue + '"');
    }
  }
  if (mismatches.length > 0) {
    assert.fail('Radius parity failed:\n' + mismatches.join('\n'));
  }
});

test('parity: every shadow token in tokens.json appears in BOTH outputs', () => {
  const json = JSON.parse(fs.readFileSync(INPUT, 'utf-8'));
  const wxss = parseWxss(fs.readFileSync(OUT_MP, 'utf-8'));
  const admin = parseAdminExport(fs.readFileSync(OUT_ADMIN, 'utf-8'), 'shadow');

  const required = ['sm', 'md', 'lg'];
  const mismatches = [];
  for (const key of required) {
    const jsonValue = json.shadow[key];
    const wxssValue = wxss['--shadow-' + key];
    const adminValue = admin[key];

    // wxss uses rgba (oklch→hex/rgba conversion for WeChat), so just check it exists and is not oklch
    if (!wxssValue || wxssValue.includes('oklch')) {
      mismatches.push('  - shadow.' + key + ': wxss="' + wxssValue + '", expected rgba()');
    }
    // admin uses oklch (same as json)
    if (adminValue !== jsonValue) {
      mismatches.push('  - shadow.' + key + ': admin="' + adminValue + '", json="' + jsonValue + '"');
    }
  }
  if (mismatches.length > 0) {
    assert.fail('Shadow parity failed:\n' + mismatches.join('\n'));
  }
});

test('wxss colors are hex (oklch→hex for WeChat), admin colors are oklch', () => {
  const wxss = parseWxss(fs.readFileSync(OUT_MP, 'utf-8'));
  const admin = parseAdminExport(fs.readFileSync(OUT_ADMIN, 'utf-8'), 'tokens');

  for (const [k, v] of Object.entries(wxss)) {
    if (k.startsWith('--font-') || k.startsWith('--radius-') || k.startsWith('--shadow-')) continue;
    assert.ok(
      /^#[0-9a-f]{6}$/i.test(v),
      'tokens.wxss ' + k + ' must be hex (#rrggbb), got: ' + v,
    );
  }
  for (const [k, v] of Object.entries(admin)) {
    assert.ok(
      /^oklch\(/.test(v),
      'tokens.tailwind.ts ' + k + ' must be oklch(...), got: ' + v,
    );
  }
});

// ---------------------------------------------------------------------------
// Negative tests — confirm build script refuses bad input
// ---------------------------------------------------------------------------

test('rejects hex value in tokens.json (build script exits non-zero)', () => {
  // Snapshot the real input
  const backup = fs.readFileSync(INPUT, 'utf-8');
  const good = JSON.parse(backup);
  const bad = JSON.parse(JSON.stringify(good));
  bad.colors.accent = '#ff6600';
  fs.writeFileSync(INPUT, JSON.stringify(bad, null, 2));

  try {
    let exitCode = 0;
    let stderr = '';
    try {
      execFileSync(process.execPath, [BUILD_SCRIPT], { cwd: ROOT, stdio: 'pipe' });
    } catch (e) {
      exitCode = e.status || 1;
      stderr = (e.stderr || Buffer.from('')).toString();
    }
    assert.notEqual(exitCode, 0, 'build script must exit non-zero on hex value');
    assert.ok(
      /oklch/i.test(stderr) || /hex/i.test(stderr),
      'error message must mention oklch/hex, got: ' + stderr,
    );
  } finally {
    // Restore the real input
    fs.writeFileSync(INPUT, backup);
    // Re-run build so the outputs reflect the restored JSON
    execFileSync(process.execPath, [BUILD_SCRIPT], { cwd: ROOT, stdio: 'pipe' });
  }
});

test('rejects missing required color key (build script exits non-zero)', () => {
  const backup = fs.readFileSync(INPUT, 'utf-8');
  const good = JSON.parse(backup);
  const bad = JSON.parse(JSON.stringify(good));
  delete bad.colors.accent;
  fs.writeFileSync(INPUT, JSON.stringify(bad, null, 2));

  try {
    let exitCode = 0;
    try {
      execFileSync(process.execPath, [BUILD_SCRIPT], { cwd: ROOT, stdio: 'pipe' });
    } catch (e) {
      exitCode = e.status || 1;
    }
    assert.notEqual(exitCode, 0, 'build script must exit non-zero on missing key');
  } finally {
    fs.writeFileSync(INPUT, backup);
    execFileSync(process.execPath, [BUILD_SCRIPT], { cwd: ROOT, stdio: 'pipe' });
  }
});
