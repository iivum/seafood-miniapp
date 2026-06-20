# Tasks — mp 视觉验证工具(C5)

> 顺序按 design Migration Plan:spike 先行(C1-C4 教训)。本地需微信 DevTools,apply 时自起。

## 1. spike(D3/D1 — 阻塞后续)

- [x] 1.1 **自起 DevTools 成功**:`cli auto --project frontend --auto-port 9420` → AppID wx382d7553c29e617c,**9420 LISTENING**,已登录无需 QR。约束①消除
- [x] 1.2 **connect + 截图链路通**(分步日志):connect 11ms ✅ → switchTab 3s ✅ → **截图 ✅ 780×1524 PNG**(390×762@DPR2 真实 home);`page.$('.home-banner')` 超时——选择器没错(wxml 确有),真因是 **home 内容需后端数据才渲染**(banner/grid 来自 products/categories),后端没跑 → loading 态 → 元素不渲染。几何层前置 = 后端起+seed(同现有 mp e2e fromBackend)
- [ ] 1.3 OD 侧:`get_artifact` 拉 `mp-01-home.html` → Playwright 渲染 golden + 量 bbox(待 §2)
- [x] 1.4 **判定:GO**。三个硬不确定项全证明可行(DevTools 自起 / automator connect / 截图)。感知层 = 截图 pipeline 已通,最具风险的一环反而最稳;几何层需后端渲染内容(编排细节)。**修正 design D1**:感知(截图)证明可端到端,几何需「后端+DevTools+automator」三者就位

> **用户选 option 2:home 一屏 + 感知层先落地,几何 + 全 9 屏 + 取代旧 test 留下游。**

## 2. golden 生成 + 首屏产物

- [x] 2.1(感知 golden 已落地)home golden 经 Playwright MCP 渲 OD `mp-01-home.html`(390×762,scale=css)→ 提交 `e2e/od-golden/mp-01-home.png`。gen-od-golden 脚本化 + 余 8 屏留下游;配方记入 `e2e/tools/README.md`
- [ ] 2.2 `od-geometry/<screen>.json`(几何层,下游)
- [x] 2.3 home golden 提交

## 3. 验证 harness(感知已落地,几何下游)

- [ ] 3.1 几何主门(下游)
- [x] 3.2 感知层 `visual-diff.cjs`:automator 截 mp 实图 → sips 归一化到 golden 尺寸 → `odiff-bin`(AA-tolerant)→ 阈值 gate + 产 diff 图。masking 留下游(home 暂无需)
- [x] 3.3 `npm run test:visual` 入口;DevTools 自起(`cli auto`)已验
- [x] 3.4 接 `mp-01-home` 跑通:**RED 46.47%**(现状无后端=loading 态 vs OD 满内容设计,真实偏离信号),产 `mp-01-home-diff.png`

## 4. 铺开 + 取代 + 文档

- [ ] 4.1 扩到余 8 mp 屏(参数化 SCREENS 已就绪,加条目 + 各屏 golden)(下游)
- [ ] 4.2 删除/取代 `mp-od-design.test.ts` 静态 grep(下游,待感知/几何铺满)
- [x] 4.3 **改 CLAUDE.md**:已废弃「像素 diff 太脆」旧说法 → 「感知 diff 主门 + 4 层辅」;`e2e/tools/README.md` 记跑法/golden 配方
- [x] 4.4 commit(见下;本 slice)

## 5. 收尾(slice 不归档,留 change 活跃做下游)

- [ ] 5.1 回填 roadmap(待 C5 全量完成)
- [ ] 5.2 归档(待几何 + 全 9 屏 + 删旧 test 完成)

> **下游 backlog**:① 几何层(od-geometry + bbox 比对,需后端渲染内容)② 余 8 屏 golden + 接入 ③ 删 `mp-od-design.test.ts` ④ 有意义信号需后端 seed(本 slice 无后端=loading 态,信号偏大但 pipeline 已验)
