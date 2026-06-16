# 2026-06-17 · Sprint 1 视觉回归验证 — Design

> 目的:对所有 mp (8) + ad (6) 共 14 屏做视觉/布局/数据流回归验证,产出 v2.1 signoff。

## 上下文

- 分支:`feat/sprint-1-closure`
- 已 commit 8 次修复 + 测试(见 sprint1-closure-checkpoint memory)
- 之前会话找到 6 个真 bug 并修了:mp `getApp()` race、Spring Page 形状、cookie auth 双层、LoginLockout 竞态、AdminCookieAuth 竞态、HTTP status 透传
- 已有覆盖:mp 6/8 (mp-01/02/03/04/06/08),ad 5/6 (ad-01/02/03/04/06)
- 缺:mp-05 订单详情 / mp-07 收货地址 / ad-05 订单列表
- 已知 WCAG AA fail(本轮 soft fail):`detail-footer__btn--cart` (3.58) / `order-list__status PENDING` (2.17)

## 范围

### In-scope

- **回归已有 11 屏**:跑 mp-3layer + 4 个 admin e2e,确认未回归
- **补缺失 e2e (3 屏)**:
  - mp-05 订单详情(frontend/pages-sub/order/order-detail)
  - mp-07 收货地址(frontend/pages-sub/user/address)
  - ad-05 订单列表(admin-ui/src/features/orders/OrderListPage)
- **重新截图 14 屏**:DevTools mp + Playwright ad,存 frontend/e2e/screenshots/ + admin-ui/screenshots/
- **重跑 chroma 颜色验证**:
  - token parity(8 tokens,ΔE < 2)
  - WCAG AA contrast(11+ 关键 CTA / status badge)
- **出 v2.1 signoff** 文档

### Out-of-scope

- 修 WCAG fail(Sprint 2 工作)
- 改 mp-04/06/08 navigation timeout(Sprint 2 工作)
- 任何功能修改 — 仅验证

## 策略:4 层断言(沿用)

| 层 | 抓什么 | mp 实现 | ad 实现 |
|---|---|---|---|
| 1 结构 | 节点/class/文案 | `page.outerWxml()` | `page.locator()` + `aria-query` |
| 2 数据 | 字段真实性 | `page.data()` + `fromBackend` | `page.evaluate(() => store.getState())` |
| 3 行为 | runtime 异常 | `miniProgram.on('console'\|'exception')` | `page.on('pageerror')` |
| 4 颜色 | token parity + WCAG | chroma.js 解析 `getComputedStyle` | 同上 |

## 执行顺序

1. **Task #1:环境 + baseline**(in_progress)
   - backend 8080 health check(已确认 UP)
   - DevTools auto-port 9420 connect
   - 跑 `npx jest e2e/mp-3layer.test.ts --runInBand` 确认基线
   - 跑 `npx vitest run` 在 admin-ui 看 5 个 e2e
2. **Task #2:补 mp-05 / mp-07**(依赖 #1)
   - 找后端端点(/api/orders/{id} / /api/addresses)
   - 在 mp-3layer.test.ts 加 2 个 PageSpec
   - 加 `fromBackend` 断言
3. **Task #3:补 ad-05 e2e**(依赖 #1)
   - OrderListPage.test.tsx 当前只有单测
   - 补 OrderListPage.e2e.test.tsx:cookie 登录 → /admin/orders → 列表渲染 → 状态 tab 切换 → 跳详情
4. **Task #4:截图 + chroma 验证**(依赖 #2/#3)
   - mp:14 个 screenshot 按 page spec 命名
   - ad:6 个 screenshot via Playwright
   - 跑 token-parity.test.ts(已存在,扩到 14 屏取色)
   - WCAG report 用 chroma.contrast(11 个 fg/bg pair)
5. **Task #5:v2.1 signoff**(依赖 #4)
   - 文档:docs/redesign/v2-visual-signoff-2026-06-17.md
   - 表格:14 屏 × 4 层 = 56 个断言点 ✓/✗
   - WCAG fail 仍 soft fail 列示
   - PR 评论式截图引用

## 风险

| 风险 | 缓解 |
|---|---|
| DevTools WebSocket 跨 it stall | 每个 spec 重新 `automator.connect()` |
| mp navigation timeout(04/06/08) | 若重复发生,改为单独跑 |
| ad-05 没 seed 订单 | 跑 `backend/seed/seed.sh` 或 mock |
| 后端 hot reload 状态污染 | 测试用 unique 订单 ID |

## 不做的事

- 不修 WCAG(soft fail 范围)
- 不引入新依赖(沿用 chroma + miniprogram-automator + Playwright)
- 不动 backend / mp 业务代码

## 验收

- 11 屏 baseline 全绿(已有 e2e)
- 3 屏新 e2e 全绿
- token parity ΔE < 2 仍 8/8
- WCAG 报告存在,soft fail 明示
- v2.1 signoff commit
- 任务 #1-#5 全 completed