# Design — mp 视觉验证工具(C5)

## Context

现状 mp 多屏与 OD 设计偏差大、部分不可用。现有 `frontend/e2e/mp-od-design.test.ts` 退化为静态 grep(查 wxml/wxss 有无 class/选择器)+ token parity —— 不看实际渲染,布局崩它照样绿。OD 真值 = Open Design 项目 `686e3434` 的 9 张 mp HTML mockup(`mp-01-home.html` … `mp-09-order-detail.html`,高保真、含真实 assets、可渲染)。原 `docs/redesign/mp-screenshots/design-ref/` PNG 已随 docs/redesign 删除。

约束:mp 跑需微信 DevTools(`cli auto --auto-port 9420`,CLI 在 `/Applications/wechatwebdevtools.app/Contents/MacOS/cli`);CI 无 DevTools → 本地 only(同现有 mp e2e)。CLAUDE.md 当前主张「4 层取代截图对图,像素 diff 对抗锯齿/DPR 极度敏感」—— 用户授权:有更优方式可废弃此说法。

## Goals / Non-Goals

**Goals**
- 几何 + 感知混合,抓"现状 vs OD 偏离 / 布局不可用",驱动 TDD 逐屏修
- OD mockup → 可提交 golden(PNG + geometry),验证不依赖测试期 OD MCP
- 取代静态 grep 验证,更新 CLAUDE.md

**Non-Goals**
- 不在 CI 跑(需 DevTools)
- 本 change 不修完所有屏 —— 建工具 + 打通 1-2 屏 RED→GREEN 示范,余屏与 admin 6 屏下游
- 不追求像素级完全一致(跨引擎不现实);几何容差 + 感知阈值,抓"明显偏离"

## Decisions

### D1:几何为主门、感知为辅(defuse 像素 diff 旧顾虑)

- **几何层**:automator 取 mp 关键区域 element rect(`boundingClientRect`),与 OD geometry(从 mockup DOM 量出、比例化到 viewport 宽高)比对,容差(如 ±3% 宽高、±2% 位置)。**对抗锯齿/DPR 完全免疫**,直接抓重叠/错位/尺寸错 = "不可用"。
- **感知层**:`odiff`(AA-aware,比 pixelmatch 更稳)比 mp 实截图 vs OD golden,阈值 + 动态文本区 masking。
- **理由**:CLAUDE.md 旧顾虑("像素 diff 太脆")只对**纯像素全等**成立;现代做法 = 几何(免疫)兜底 + 感知(AA-tolerant)抓外观。几何为主保证"不可用"必被抓且不 flaky;感知为辅补外观,跨引擎噪声大时降级仍有几何主门。

### D2:OD golden = mockup 渲染,提交进仓

golden 生成脚本(本地,连 OD MCP 时跑):`get_artifact` 拉每屏 mockup HTML + assets → Playwright headless 渲染至 mp viewport(宽 375 CSS px、DPR 锁 2、高按内容)→ 截 `od-golden/<screen>.png` + 注入脚本量关键区域 bbox 写 `od-geometry/<screen>.json` → 提交。验证测试只读已提交产物,不连 OD MCP(符合 spec)。

### D3:验证 harness 跑法 = 自起 DevTools + automator

测试侧(jest e2e):脚本 `cli auto --auto-port 9420` 起 DevTools 自动化端口 → automator connect → 逐屏导航 → 取 element rect(几何)+ 截图(感知)→ 比对。我在 apply 时自起 DevTools 自验,不需用户手动配。

### D4:关键区域而非全页

几何/感知都按"每屏关键区域清单"(顶部栏 / banner / 内容网格 / 操作区 / tabbar)做,而非全页一张图比。理由:① 区域级 diff 定位精确(知道哪块崩)② 避开整页里高动态内容(商品图/价格)的噪声 ③ 容差可分区域调。

## Risks / Trade-offs

- **[DevTools CLI automator 起不来 / 需登录态]** → Mitigation:Task 1 spike 先验自起 + connect + 截 1 张图;若需交互登录,记录最小手动前置(一次性),其余自动化。
- **[跨引擎感知 diff 噪声盖信号]** → Mitigation:D1 几何为主门(免疫);感知噪声大则调高阈值/扩 masking,甚至本 change 内降级为"仅产出 diff 图供人看"、不 gate。spike Task 1 实测 1 屏定调。
- **[OD mockup 与 mp 布局本就不 1:1(设计意图 vs 实现)]** → Mitigation:几何按"区域相对位置/比例"而非绝对像素;容差给足;关键区域清单人工锚定。
- **[golden 生成依赖 OD MCP,CI/他人环境无]** → Mitigation:golden 提交进仓,生成是按需离线步骤;验证只读 golden。

## Migration Plan

1. **spike**(Task 1):自起 DevTools + automator 截 1 屏(如 home)+ 渲 OD home mockup + 试几何/odiff,定"感知能否 gate"
2. golden 生成脚本 + 首屏 golden/geometry 提交
3. 几何 + 感知 harness,接首屏(现状 RED → 记录偏离)
4. 扩到首批 mp 屏(工具铺开,不强求全修绿)
5. 删静态 grep + 改 CLAUDE.md + README + 归档

**Rollback**:纯 test/脚本 + 资源文件,无生产影响,删之即可。

## Open Questions

- DevTools `cli auto` 在本机能否无人值守起自动化端口(登录态/service-mode)?→ Task 1 spike 实测。
- 跨引擎 odiff 在合理容差下是否还有有用信号,还是只能几何为主?→ Task 1 单屏实测定调。

## Sources

- Open Design 项目 `686e3434`(`list_files`:mp-01..09 + ad-01..06 HTML mockup)
- [odiff](https://github.com/dmtrKovalenko/odiff) — AA-aware 感知 diff
- 既有 `frontend/e2e/mp-3layer.test.ts` — automator 连 DevTools 的现成模式
