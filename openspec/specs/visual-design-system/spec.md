# Spec: visual-design-system

## Purpose

[TBD — see change `v2-visual-redesign` for context. Defines the canonical OKLch-based design token system, the build step that generates WXSS and Tailwind theme from a single JSON source, the parity test, and the six team-level design postures.]

## Requirements

### Requirement: Single source of truth for design tokens
The system SHALL maintain a single JSON file `docs/redesign/tokens.json` as the canonical source of all design tokens (colors, typography, radii, shadows). All consumer surfaces (mp WXSS, admin Tailwind theme) MUST be generated from this file via the build step `scripts/build-tokens.js`. The JSON file MUST contain at minimum 15 base color tokens (4 backgrounds + 4 muted + 4 border + 4 accent variants + 4 status colors) and 4 state-soft variants.

#### Scenario: Token JSON present and well-formed
- **WHEN** a developer inspects `docs/redesign/tokens.json`
- **THEN** the file contains a `colors` object with at minimum: `bg`, `surface`, `fg`, `muted`, `soft`, `border`, `border-strong`, `accent`, `accent-soft`, `accent-strong`, `accent-deep`, `success`, `warning`, `error`, `info` and a `state-soft` map for `success-soft`, `warning-soft`, `error-soft`, `info-soft`
- **AND** the file contains a `typography` object with `display`, `body`, `mono` font stacks
- **AND** the file contains a `radius` object with 6 named values: `sm`, `md`, `lg`, `xl`, `2xl`, `3xl`, plus `pill`
- **AND** the file contains a `shadow` object with 3 named values: `sm`, `md`, `lg`

#### Scenario: Token JSON rejected on missing required key
- **WHEN** a developer removes one of the required color tokens (e.g. `accent`) from `tokens.json`
- **THEN** the CI build step `npm run build:tokens` exits with a non-zero code
- **AND** emits an error message naming the missing key

---

### Requirement: Build step generates WXSS for mini-program
The system SHALL include a build step that reads `docs/redesign/tokens.json` and generates `frontend/src/shared/tokens/tokens.wxss` containing CSS custom properties for every token. The generated WXSS file MUST use `oklch(...)` color function for all color values. The build step SHALL be invokable via `npm run build:tokens` from the repository root.

#### Scenario: Build step produces valid WXSS
- **WHEN** a developer runs `npm run build:tokens`
- **THEN** the file `frontend/src/shared/tokens/tokens.wxss` is created (or overwritten)
- **AND** it contains lines of the form `--<token-name>: <value>;` for every token in the JSON
- **AND** all color values use `oklch(...)` syntax
- **AND** the build step exits with code 0

#### Scenario: Build step regenerates after token change
- **WHEN** a developer edits `docs/redesign/tokens.json` to change `--accent` value
- **THEN** running `npm run build:tokens` produces an updated `tokens.wxss` with the new value
- **AND** the previous value is no longer present in `tokens.wxss`

---

### Requirement: Build step generates Tailwind theme for admin-ui
The system SHALL include the same build step generating `admin-ui/src/shared/tokens/tokens.tailwind.ts` exporting a typed `tokens` object whose values mirror `tokens.json`. The admin `tailwind.config.ts` MUST import this object and inject it into the Tailwind theme as `colors`, `fontFamily`, `borderRadius`, and `boxShadow` extensions.

#### Scenario: Build step produces valid TypeScript tokens
- **WHEN** a developer runs `npm run build:tokens`
- **THEN** the file `admin-ui/src/shared/tokens/tokens.tailwind.ts` is created (or overwritten)
- **AND** it exports a `tokens` constant whose keys mirror the JSON structure
- **AND** `admin-ui/tailwind.config.ts` contains `import { tokens } from '<...>/tokens.tailwind'`
- **AND** the Tailwind `theme.extend.colors` object exposes `bg`, `fg`, `accent`, `success`, `error`, etc. by name

---

### Requirement: Cross-surface token parity test
The system SHALL include a unit test that parses both `frontend/src/shared/tokens/tokens.wxss` and `admin-ui/src/shared/tokens/tokens.tailwind.ts` and asserts that every token defined in `docs/redesign/tokens.json` is present in BOTH generated files with the same value. The test SHALL run in CI on every PR.

#### Scenario: Parity test passes when both files match
- **WHEN** CI runs the parity test
- **AND** `tokens.wxss` contains `--accent: oklch(64% 0.16 38);`
- **AND** `tokens.tailwind.ts` contains `accent: 'oklch(64% 0.16 38)'`
- **THEN** the test passes

#### Scenario: Parity test fails when one side drifts
- **WHEN** a developer manually edits `tokens.wxss` to change `--accent` value
- **AND** does NOT regenerate from the JSON
- **THEN** CI parity test fails
- **AND** the failure message names the mismatched token and both values

---

### Requirement: Three-font typography system
The system SHALL define a three-font typography system: `display` (serif — `Fraunces` stack), `body` (sans-serif — `Inter Tight` stack), and `mono` (monospace — `Geist Mono` stack). The mp surface MUST use `@font-face` declarations to bundle the fonts as static assets; the admin surface MUST consume `fontsource` npm packages for the same three families.

#### Scenario: mp font assets bundled
- **WHEN** a developer inspects `frontend/assets/fonts/`
- **THEN** the directory contains subset font files (`.woff2`) for Fraunces, Inter Tight, and Geist Mono
- **AND** `frontend/src/shared/tokens/fonts.wxss` (or equivalent) contains `@font-face` declarations referencing these files
- **AND** the total bundled font size is no greater than 250 KB

#### Scenario: admin-ui imports fontsource packages
- **WHEN** a developer inspects `admin-ui/package.json`
- **THEN** the file lists `@fontsource/fraunces`, `@fontsource/inter-tight`, `@fontsource/geist-mono` as dependencies
- **AND** the admin-ui Tailwind theme `fontFamily.display` references the `Fraunces` family
- **AND** `fontFamily.body` references `Inter Tight`
- **AND** `fontFamily.mono` references `Geist Mono`

---

### Requirement: Six design postures written into team documentation
The system SHALL document six explicit design postures in `docs/DESIGN.md`:
1. **Primary actions are solid accent color, NOT 135° gradients** — single brand color flat
2. **Shadows are blue-neutral, NOT red-tinted** — visually calm; "freshness" comes from serif typography and photography, not shadow
3. **Prices use ink color or accent, NOT competing visual weight** — display serif, numerals have culture
4. **Serif display conveys "fresh / authentic", NOT a generic trendy feel** — Fraunces is the key differentiator
5. **Single accent used consistently, AT MOST twice per screen** — `accent` count ≤ 2 per rendered page
6. **State colors ONLY for state, NOT brand storytelling** — green/gold/red do not appear in brand layer

#### Scenario: DESIGN.md documents all six postures
- **WHEN** a developer reads `docs/DESIGN.md`
- **THEN** the file contains a section enumerating all six postures with their rationale
- **AND** each posture is phrased as a SHALL or MUST normative rule

#### Scenario: Posture 5 enforced at lint time (Sprint 1 stretch)
- **WHEN** Sprint 1 concludes a `lint:accent-count` script is available
- **THEN** running the script against any mp wxml or admin JSX page produces a warning if the rendered page uses `accent` color more than twice

---

### Requirement: App-level integration of token system
The system SHALL integrate the generated token files into the application entry points: `frontend/app.wxss` MUST `@import '/shared/tokens/tokens.wxss';` at the top, and `admin-ui/src/main.tsx` (or equivalent root) MUST apply Tailwind classes mapped to tokens to the root `<html>` or `<body>` element.

#### Scenario: mp app.wxss imports tokens
- **WHEN** a developer opens `frontend/app.wxss`
- **THEN** the first non-comment line is `@import '/shared/tokens/tokens.wxss';`
- **AND** the import resolves at WeChat DevTools build time without error

#### Scenario: admin-ui root element uses token-based classes
- **WHEN** a developer opens `admin-ui/src/main.tsx` or the root component
- **THEN** the root element has at least one class derived from token names (e.g. `bg-bg text-fg`)
- **AND** the corresponding Tailwind classes resolve to OKLch values at build time

---

### Requirement: Token JSON rejects hex values
The system SHALL reject hex color values in `docs/redesign/tokens.json`. The build step MUST emit an error if any color value is not in `oklch(...)` form. Existing hex values that must remain (e.g. `app.json` `navigationBarBackgroundColor` because WeChat native nav bar does not support CSS variables) MUST be expressed as a hardcoded *fallback* in the consuming app entry file, NOT in the token JSON.

#### Scenario: Build step rejects hex value in tokens.json
- **WHEN** a developer adds `"accent": "#ff6600"` to `tokens.json`
- **THEN** `npm run build:tokens` exits with non-zero code
- **AND** the error message reads "all color values MUST be oklch(...), got: #ff6600 at colors.accent"

#### Scenario: Hex fallbacks documented separately
- **WHEN** a developer inspects `frontend/app.json`
- **THEN** any hex color present is documented in a code comment as "hex fallback for native nav bar — NOT in tokens.json because WeChat native nav bar does not support CSS variables"
- **AND** the hex value matches the corresponding token's OKLch value when displayed (no perceptual drift)
