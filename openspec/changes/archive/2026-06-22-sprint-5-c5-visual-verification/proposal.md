## Why

现状 mp 多屏视觉与 OD 设计有较大偏差,部分界面处于不可用状态(布局崩/重叠/尺寸错)。但现有 `mp-od-design.test.ts` 只做**静态 grep**(查 wxml/wxss 里有没有对应 class/选择器)+ token 颜色 parity —— 一个屏 class 齐全、token 对,布局照样可以崩成不可用,它仍然全绿。CLAUDE.md「4 层取代截图对图」的盲区正在于此:它**不看实际渲染几何/外观**。按 TDD 测试先行,要**先有一个能验证"现状 vs OD 设计差距"的工具**(现状 RED),再以它驱动逐屏修复,并持续防偏。这正是 roadmap C5 的真实动机(非"重设计 churn")。

## What Changes

- 新增 mp 视觉验证工具,以 Open Design 项目(`686e3434`,9 张 mp HTML mockup)为 OD 真值
- **几何层(主门,抗锯齿/DPR 免疫)**:量 mp 关键区域 element rect,与 OD mockup 量出的 region 几何比对(比例化 + 容差)→ 直接抓"布局崩/不可用"
- **感知层**:`odiff` 比 mp 实截图 vs OD mockup 渲染的 golden(容差 + 动态文本区 masking)→ 抓外观偏离
- golden 生成脚本:OD MCP 拉 mockup HTML → Playwright 渲染至 mp viewport + 量 bbox → 提交 golden PNG + geometry JSON
- **取代** `mp-od-design.test.ts` 静态 grep;**改写 CLAUDE.md**「4 层取代截图」→「几何+现代 AA-tolerant 感知 diff 主门 + token/结构辅助层」
- TDD:现状屏 RED → 驱动逐屏修 → GREEN → golden 锁定防偏。本 change 建工具 + 打通 1-2 屏示范;修完所有屏 + admin 6 屏是下游

## Capabilities

### New Capabilities
- `visual-verification`: mp 视觉验证工具的 OD 真值来源、几何+感知混合主信号、golden 生成与提交、本地 DevTools 运行约束、TDD 修复/防偏闭环、与旧静态验证的取代关系

### Modified Capabilities
<!-- 无 spec 级行为契约变更;CLAUDE.md 描述更新是文档调整,不建 delta spec -->

## Impact

- 新增 `frontend/e2e/` 视觉验证 harness(几何 + 感知)+ golden 生成脚本 + `od-golden/`(PNG)+ `od-geometry/`(JSON)
- 删除/取代 `frontend/e2e/mp-od-design.test.ts` 静态 grep
- 改写 `CLAUDE.md` 视觉验证段
- 新增 `odiff`(或同类感知 diff)+ Playwright(渲 OD mockup)test 依赖
- **运行约束**:需本地微信 DevTools(`cli auto --auto-port 9420`,CLI 已确认在);**CI 不跑**(同现有 mp e2e),本地 + 按需
- **风险(spike 先行)**:① DevTools CLI automator 能否起+截图(首次或需登录态)② 跨引擎(OD 浏览器渲染 vs 微信渲染)感知 diff 噪声是否盖过信号 → 噪声大则几何为主、感知降级辅助
- 无后端改动,无 API 变更,无 BREAKING
