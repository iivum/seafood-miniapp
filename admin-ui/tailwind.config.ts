import type { Config } from 'tailwindcss';
import animate from 'tailwindcss-animate';
import { tokens, typography, radius, shadow } from './src/shared/tokens/tokens.tailwind';

/**
 * OD v2 design tokens (Sprint 0 1.22 切流)— 消费 ./src/shared/tokens/tokens.tailwind.ts
 * 单一源:docs/redesign/tokens.json(经 scripts/build-tokens.js 生成 tokens.tailwind.ts)。
 *
 * v1 nested (`primary-500` / `app-muted` / `feedback-error` / `h1` / `body` / `xs`)
 * 全部移除,只保留 v2 flat(`bg-accent` / `text-muted` / `text-error` / `text-2xl` 等)。
 *
 * 与 mp tokens.wxss 的对应关系见 `scripts/__tests__/build-tokens.test.js`(9/9 parity)。
 */
const config: Config = {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      // OD v2 19 个颜色 token(扁平)— 不再嵌套
      colors: {
        // surface
        bg: tokens.bg,
        surface: tokens.surface,
        fg: tokens.fg,
        muted: tokens.muted,
        soft: tokens.soft,
        border: tokens.border,
        'border-strong': tokens['border-strong'],
        // accent
        accent: tokens.accent,
        'accent-soft': tokens['accent-soft'],
        'accent-strong': tokens['accent-strong'],
        'accent-deep': tokens['accent-deep'],
        // state
        success: tokens.success,
        warning: tokens.warning,
        error: tokens.error,
        info: tokens.info,
        'success-soft': tokens['success-soft'],
        'warning-soft': tokens['warning-soft'],
        'error-soft': tokens['error-soft'],
        'info-soft': tokens['info-soft'],
      },
      // 字体:display / body / mono(与 tokens.wxss 字体链一致)
      fontFamily: {
        display: [typography.display],
        body: [typography.body],
        mono: [typography.mono],
        // 保留 Tailwind 默认 font-sans 链(作为 v1 fallback,新代码用 font-body)
        sans: [typography.body],
      },
      // 圆角:v2 9 档(sm/md/lg/xl/2xl/3xl/22/28/pill)— 数字键名以
      // 字符串形式传给 tailwind(否则会尝试当数字 parse)
      borderRadius: {
        none: '0',
        sm: radius.sm,
        md: radius.md,
        lg: radius.lg,
        xl: radius.xl,
        '2xl': radius['2xl'],
        '3xl': radius['3xl'],
        '22': radius['22'],
        '28': radius['28'],
        pill: radius.pill,
        // 保留 Tailwind 默认 `full`(等同 v2 pill),允许 v1 `rounded-full` 写法继续编译
        // (本次迁移将所有 v1 `rounded-full` 都改为 `rounded-pill`,但保留这里兜底)
        full: radius.pill,
      },
      // 阴影:v2 仅 3 档(sm/md/lg),不再有 xl
      boxShadow: {
        sm: shadow.sm,
        md: shadow.md,
        lg: shadow.lg,
        // 兼容:把 v1 残留 shadow-xl 降级到 lg(Sprint 1 末重审)
        xl: shadow.lg,
      },
      // OD v2 显式断字距 zIndex(保留以兼容 admin-ui 现有 component 引用)
      zIndex: {
        sidebar: '40',
        header: '30',
        dropdown: '50',
        modal: '60',
        toast: '70',
      },
      transitionDuration: {
        fast: '150ms',
        normal: '250ms',
        slow: '350ms',
      },
    },
  },
  plugins: [animate],
};

export default config;
