# Tasks — mp 视觉验证工具(C5)

> 顺序按 design Migration Plan:spike 先行(C1-C4 教训)。本地需微信 DevTools,apply 时自起。

## 1. spike(D3/D1 — 阻塞后续)

- [x] 1.1 **自起 DevTools 成功**:`cli auto --project frontend --auto-port 9420` → AppID wx382d7553c29e617c,**9420 LISTENING**,已登录无需 QR。约束①消除
- [x] 1.2 **connect + 截图链路通**(分步日志):connect 11ms ✅ → switchTab 3s ✅ → **截图 ✅ 780×1524 PNG**(390×762@DPR2 真实 home);`page.$('.home-banner')` 超时——选择器没错(wxml 确有),真因是 **home 内容需后端数据才渲染**(banner/grid 来自 products/categories),后端没跑 → loading 态 → 元素不渲染。几何层前置 = 后端起+seed(同现有 mp e2e fromBackend)
- [ ] 1.3 OD 侧:`get_artifact` 拉 `mp-01-home.html` → Playwright 渲染 golden + 量 bbox(待 §2)
- [x] 1.4 **判定:GO**。三个硬不确定项全证明可行(DevTools 自起 / automator connect / 截图)。感知层 = 截图 pipeline 已通,最具风险的一环反而最稳;几何层需后端渲染内容(编排细节)。**修正 design D1**:感知(截图)证明可端到端,几何需「后端+DevTools+automator」三者就位

## 2. golden 生成脚本 + 首屏产物

- [ ] 2.1 `frontend/e2e/tools/gen-od-golden.*`:OD MCP 拉 mockup HTML+assets → Playwright 渲染固定 viewport → 写 `od-golden/<screen>.png`
- [ ] 2.2 注入量测脚本产 `od-geometry/<screen>.json`(关键区域比例化 bbox + label)
- [ ] 2.3 跑出 `mp-01-home` 的 golden + geometry 并提交;脚本记一次性运行说明(依赖 OD MCP)

## 3. 验证 harness(几何 + 感知)

- [ ] 3.1 几何主门:辅助函数读 `od-geometry/<screen>.json`,与 automator 取的 mp rect 比例化比对(容差 ±3%),失败报偏离区域 + 实际/期望 bbox
- [ ] 3.2 感知层:`odiff` mp 截图 vs `od-golden/<screen>.png`,阈值 + 动态文本区 masking;按 §1.4 结论决定 gate 或仅产 diff 图
- [ ] 3.3 起测脚本:自起 DevTools(§1.1)+ 跑 jest;封装成 `npm run test:visual` 类入口
- [ ] 3.4 接 `mp-01-home`:现状跑出结果(RED 则记录偏离点驱动修复;不强求本 change 修绿)

## 4. 铺开 + 取代 + 文档

- [ ] 4.1 几何/感知 harness 扩到首批 mp 屏(参数化屏清单);余屏 golden 增量生成
- [ ] 4.2 删除/取代 `frontend/e2e/mp-od-design.test.ts` 静态 grep
- [ ] 4.3 改写 `CLAUDE.md` 视觉验证段:「4 层取代截图」→「几何+感知 diff 主门(对 OD golden)+ token/结构辅助」;`README` 加 `npm run test:visual` 说明
- [ ] 4.4 commit:`test(visual): C5 mp 视觉验证 — 几何+感知 对 OD golden`

## 5. 收尾

- [ ] 5.1 回填 `openspec/changes/test-suite-roadmap/tasks.md`:C5 done(真实动机 = 现状偏离 OD,工具形态 = 几何+感知,非"重设计触发")
- [ ] 5.2 `/opsx:archive sprint-5-c5-visual-verification` 归档并 sync `visual-verification` spec 到 `openspec/specs/`
