---
name: seafood-mp-knowledge
description: |
  Seafood Mini-Program (mp) 项目知识索引 + 红线速查。在任何 seafood-miniapp frontend mp 相关工作中触发:
  写新页面、调试 mp 运行时错误、重构 shim 路径、改登录/导航/网络层、评估 Skyline vs WebView 决策。
  触发关键词:mp / wx.login / wx.request / subpackage / shim require / skyline / vant / navigationStyle custom /
  dev-login / wechat-login / Page<T> content / isWxFail / mp_getLogs error / color-mix in oklch / getAppSafe /
  isWxFail 误判 / 启动期崩 / 加购拦截 / 鉴权守卫 / WebView 降级 / DevTools 9420。
---

## 何时用我

- 改 mp 任何代码前对照下方 12 条红线
- 调试 mp 运行时错误(白屏 / `module 'xxx' is not defined` / `request is not a function` / order-list 空 / 401/403/404)
- 评估是否启 Skyline / 是否加 sub-package / 改 token 体系
- 写新 e2e 自动化、视觉验证、CI 集成

## 项目事实状态(本项目当前 snapshot)

- 渲染层:**已降级 WebView**(2026-06-26 决策)→ 见 [[mp-frontend-rootcause-2026-06]]
- 4 tab 主页 + 3 sub-packages(product / order / user)
- 9 屏视觉 baseline 全 RED 29-71%(5% 阈值是目标非现状)
- 几何层 GREEN 4/9(mp-01 home / mp-02 category)
- 后端 8080 + 9090 actuator,GraalVM Native;admin-ui 5173;mp DevTools 9420

## 12 条红线检查表

| # | 红线 | 触发场景 | 修法 / 来源 |
|---|------|----------|------------|
| 1 | WXML 用 `?.` 可选链 / 内嵌函数调用 / 嵌套花括号 >2 层 | 新写 wxml | 拆 JS 数据预计算,见 ref 01 |
| 2 | WXSS 用 `gap` / `display:grid` / `color-mix(in oklch, ...)` | 新写 wxss | `margin-right/bottom` / `flex-wrap` / hex 直写;见 ref 07 + [[skyline-wxss]] |
| 3 | 新 page.json 缺 `navigationStyle:"custom"`(Skyline 时代遗留) | 加新 page | 全 page 都加,直到 WebView 降级完全落地;`pages/profile/profile.json` 当前缺 |
| 4 | OKLch token 抄进 wxss / nav bar 用 `var(--accent)` | 加新 token / 改 app.json | `docs/redesign/tokens.json` 是 SoT,`npm run build:tokens` 转 hex 给 `tokens.wxss`;nav bar 必须 hex 直写 |
| 5 | `src/shared/api/request.ts` 改了忘改 `request.js`(或反之) | 改请求层 | 双文件 lockstep,改完跑 `npm test -- --testPathPattern="__shim-require"` 验证;`isWxFail` 4 周埋藏雷的根因 |
| 6 | `require('../../utils/request.js')` 写 `const request = require(...)` 不解构 | 新 require 路径 | 必须 `const { request } = require(...)`;`address-list` 历史因此崩过;见 ref 03 |
| 7 | API 端点拼错(双 `/api` / `/confirm` 错成 `/confirm-receive`) | 新后端调用 | curl 后端验证;后端 `OrderController` 路径 SoT |
| 8 | shim require 路径层级错 | 移文件 / 加 sub-page | `src/features/<x>/api.js → ../../shared/api/request`(2 级);`pages-sub/<x>/<y>/<z>.js → utils/(4 级);跑 `__shim-require.test.ts` |
| 9 | mock 形态不 match 真实 mp runtime | 写单测 | mock 必须含 `errMsg` + `statusCode`(number) + 解构形态,否则假绿;见 [[c5-visual-test-runbook]] §2.4 |
| 10 | 自动化代码用 `page.$` / `page.outerWxml` / `mockWxMethod` getStorage | 写 e2e | 本环境 automator 0.12.1 全挂死;唯一可行 = `wx.createSelectorQuery()` 经 `mp.evaluate`;见 ref 06 + [[seafood-mp-e2e-debug]] |
| 11 | dev-login userId 漂移导致 seed 不命中 | 写 e2e fixture | seed 必须挂"当前 JWT sub 解析出的 userId",静态 fixture 不可行;见 [[c5-visual-test-runbook]] §4.1b |
| 12 | 想给 sub-page 加 `hover-class` 到 scroll-view 子元素 | 加 hover 反馈 | scroll-view 内部 hover 行为未实证,先外层 view 包裹测试;基线已是"全屏覆盖 hover-class" |

## 必看项目 skill / memory

- [[seafood-mp-e2e-debug]] — mp E2E 静默失败调试、shim 路径 fix、DevTools cache 完整流程
- [[mp-frontend-rootcause-2026-06]] — 2026-06-26 阻塞根因清单 + WebView 降级决策
- [[c5-visual-test-runbook]] — 一键触发 + 后端缺口实录
- [[visual-verification-patterns]] — 4 层方法 + gotcha(getAppSafe / Page<T> / cookie domain)
- [[sprint1-closure-checkpoint]] — Sprint 1 mp 真 bug 历史
- [[test-suite-roadmap]] — C5 行(感知/几何 / automator 0.12.1 坑)

## Skyline 主题(6 个)

- [[skyline-config]] — app.json / 页面 json
- [[skyline-components]] — scroll-view / swiper / 表单
- [[skyline-overview]] — 架构 / 性能 / 迁移
- [[skyline-route]] — 7 种预设 wx:// 路由
- [[skyline-scroll-api]] — 3 大 API 族群
- [[skyline-worklet]] — worklet / SharedValue / 动画驱动
- [[skyline-wxss]] — CSS 支持表(本项目已知影响清单见 ref 07)

## 通用 mp 工具

- [[wechat-miniprogram-e2e]] — miniprogram-automator 完整 SDK(本项目坑见 ref 06)
- [[find-docs]] — 查 wx/Skyline 最新 API(临时取,不入库)

## 本项目决策表(快速判断)

| 决策点 | 答案 | 决策时间 | 来源 |
|--------|------|----------|------|
| 渲染层 | WebView(Skyline 暂关) | 2026-06-26 | mp-frontend-rootcause-2026-06 |
| Token SoT | `docs/redesign/tokens.json`(OKLch),build step 转 hex | Sprint 0 | d2e668b commit |
| 字体策略 | Fraunces / Inter Tight / Geist 子集(unicode-range 慎用) | Sprint 0 | d2e668b commit |
| sub-packages | product / order / user(merchant 已删) | Sprint 1 末 | 3e64b9e commit |
| Login | dev-login / wx.login code(getPhoneNumber 暂留) | 现状 | login.wxml:12 |
| 双 request 层 | 共存(迁移中,新代码走 src/shared) | 现状 | utils/request.js + src/shared/api/request.{ts,js} |
| 视觉 baseline | 9 屏 RED 29-71%(目标 5%) | 2026-06-22 | C5 归档 |
| 几何 GREEN | 4/9(mp-01 home / mp-02 category) | 2026-06-22 | C5 归档 |
| e2e 在 CI | 不跑(CI 排除 e2e/),只 wxss parity 跑 | 现状 | package.json jest config |
| P0-1 启动崩 / P0-2 Skyline / P1 资源+鉴权 / P2 登录 | 已修(7 commit / 14/14 e2e static+API PASS / 7/7 live placeholder SKIP / 275 全套无回归) | 2026-06-26 | [[mp-blocker-fix-2026-06-26]] |

## 调用入口(各场景去哪)

| 场景 | 入口 |
|------|------|
| 改 mp 代码前查 12 红线 | 本文件第 4 段 |
| 看具体主题(启动/登录/网络/导航/鉴权/端口/渲染) | `references/01-07` |
| 调试 mp 静默失败 / shim 路径 / DevTools cache | [[seafood-mp-e2e-debug]] |
| 写新 e2e 自动化 / 视觉验证 / 跑 mp-3layer | `frontend/e2e/tools/run-visual.sh` + [[wechat-miniprogram-e2e]] |
| 跑后端起 + seed + dev-login 一条龙 | [[c5-visual-test-runbook]] |
| 查 wx / Skyline 最新 API | [[find-docs]] |
| 浏览器自动化(不是 mp) | agent-browser(本 skill 不管) |
| 后端 Spring / DDD 写 Java | iivum-java-style(本 skill 不管) |

## 官方文档速查(5 个常用入口)

| 主题 | 官方地址 |
|------|----------|
| mp 总入口 | https://developers.weixin.qq.com/miniprogram/dev/framework/ |
| 渲染层 / Skyline | https://developers.weixin.qq.com/miniprogram/dev/framework/runtime/skyline/ |
| WXSS 组件 | https://developers.weixin.qq.com/miniprogram/dev/component/ |
| API(wx.*) | https://developers.weixin.qq.com/miniprogram/dev/api/ |
| 自定义组件 | https://developers.weixin.qq.com/miniprogram/dev/framework/custom-component/ |

> **不要全量爬官方站**。本 skill 写一次 + 按经验被动补。如果某主题在 skill 里没有,先用 `find-docs` 查,稳定后回填对应 ref。

## 维护说明

- **被动补原则**(superpowers:writing-skills):踩坑 → 立即在对应 ref / 红线表加 1 条
- 不主动跟 mp 基础库版本同步(成本 > 收益)
- 双处维护是债:**改 ref 必改红线表交叉引用**
- 任何 .ts 改了**必须**同步改 .js(锁 step,见 ref 03)
- 任何新 page.json 加 `navigationStyle:"custom"` 直到 WebView 完全落地
- 任何 e2e 跑前先 `cli auto --project frontend --auto-port 9420`
