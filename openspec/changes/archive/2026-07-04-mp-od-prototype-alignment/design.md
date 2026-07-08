# Design: mp × OD 原型逐屏对齐

## Context

C5 视觉验证 harness（感知层 `test:visual` + 几何层 `test:geometry`）在 2026-06-22 归档时已确认 9 屏 baseline 全部 RED（仅 home 靠几何层修复到 GREEN）。归档时把逐屏修复列为下游 backlog，此后无人认领。用户人工验收再次证实了这个偏离仍然存在，且伴随功能性 bug。

工具链完好、golden/geometry 基准齐全（`frontend/e2e/od-golden/*.png` + `frontend/e2e/od-geometry/*.json` 覆盖全部 9 屏），缺的只是执行。

## Goals / Non-Goals

**Goals：**
- 9 屏按业务关键路径顺序逐个对齐 OD 原型，达到感知 diff ≤ 5%（或几何全绿 + 记录在案的感知残差）
- 顺带修复诊断过程中发现的功能性 bug
- 为当前缺失 layout requirement 的 5 屏（mp-05/06/07/08 完整布局/09）在 `openspec/specs/mini-program/spec.md` 补齐

**Non-Goals：**
- 不改视觉验证工具本身（`visual-diff.cjs` / `geometry-diff.cjs`），已验证可用
- 不追求 100% 像素级 diff=0，AA/DPR 残留噪声在几何全绿时可接受
- 不在本 change 内做超出"对齐原型"范围的新功能

## Decisions

### D1：修复顺序 —— 业务关键路径

home → category → product-detail → cart → order-confirm → address → order-list → order-detail → profile

理由：按购物转化链路走一遍，最高频场景先到位；比按偏离程度倒序或 mockup 编号顺序更贴近用户实际感知优先级。

### D2：先重新跑 harness 拿当前真实 baseline，不用 C5 的旧数据

C5 数据是 6/22 的，过去一周有登录改造（P2）、7 页面 hover-class 补齐（S-2）、后端驱动 banner 接入等改动，可能已经让部分屏状态变化（甚至更差）。每屏开始诊断前必须重新跑 `test:visual <screen>` + `test:geometry <screen>` 拿真实当前数据，不能凭 C5 归档记录直接下手改。

### D3：范围包含顺带 bug 修复

样式对齐和功能性 bug 经常交织（C5 实录：几何层诊断顺带挖出 `isWxFail` 误判这种影响全局的根因 bug）。诊断阶段如发现导致页面不可用/数据缺失的真 bug，用 `systematic-debugging` 定位根因后随该屏一并修，不单开 change、不为了"保持范围纯净"而放过更严重的问题。

已知两个待复核项：
- mp-07 address：C5 记录为后端无 `AddressController` → 403；当前代码里 `AddressController.java` 已存在，需要在该屏诊断时确认是否已解决
- mp-06 order-confirm：C5 记录直达该页会构建出空购物车状态，需要诊断确认现状

### D4：执行方式 —— 混合模式（诊断在控制器，修复派 subagent）

每屏循环：

```
1. [控制器] 跑 test:visual <screen> + test:geometry <screen> → 当前 diff% + 偏离区域 + diff 图
2. [控制器] 对照 OD golden（frontend/e2e/od-golden/<screen>.png）+ diff 图，列出具体偏离点
   （例：结构性问题如"grid 应 2 列显示为 1 列"，token 问题如"用了 v1 class 而非 CSS 变量"）
3. [控制器] 写 task brief：偏离清单 + diff 图路径 + 涉及文件路径 + 相关 spec requirement 原文
4. [控制器] 用 superpowers:subagent-driven-development 派一个新 implementer subagent 改代码
5. [subagent] task reviewer 复查 spec 符合度 + 代码质量
6. [控制器] 重跑 harness 验证 diff% ≤ 5% + 几何层结构不变量全绿
7. [控制器] commit，进下一屏
```

理由：诊断阶段需要 WeChat DevTools / miniprogram-automator MCP 工具，这些只有控制器会话装载；修复阶段是较为机械的 wxml/wxss/js 编辑，适合独立 context 的 subagent，避免 9 屏信息在同一个会话里持续累积互相干扰。

### D5：Spec 改动范围

`openspec/specs/mini-program/spec.md`：
- mp-01~04 已有「OD-aligned layout」requirement，**不改 requirement 文本**，只需让实现达标；但把过时的验证方式描述（"miniprogram-automator screenshot + haiku image comparison"）更新为现用的 odiff 感知层 + 几何层机制说明
- 新增 5 条「OD-aligned layout」requirement（mp-05 profile / mp-06 order-confirm / mp-07 address / mp-08 order-list 完整布局 / mp-09 order-detail），格式仿照已有的 mp-01~04：区域结构描述 + token 约束 + 2-3 个 scenario

`openspec/specs/visual-verification/spec.md`：**不改**。该 capability 描述的是验证工具能力本身（golden 生成、几何主门、感知辅助、TDD 闭环），已经满足；"9 屏全部 GREEN"是结果性状态，不是这个 capability 的 requirement。

## Risks / Trade-offs

- **感知阈值可能压不到 5%**：odiff 仍有 AA/DPR 残留噪声（C5 已知：home 几何全绿时感知不一定同步达标）。缓解：几何层全绿即可通过，感知残差记录在 tasks.md 对应任务备注里，不阻塞进下一屏。
- **分包带参页 harness 曾经不稳定**：mp-06/07/08/09 需要登录态 + 数据种子，C5 记录过 reLaunch 偶发导航失败，但已在 commit `c39546d` 稳定化（reLaunch→注入登录态→navigateTo→校验落点+重试）。本次直接复用该修复，预期不会重新踩坑，但诊断阶段如遇到旧问题复现，按同样思路处理。
- **后端环境依赖重**：需要 `./gradlew bootRun`（当前源码）+ mongo + seed 全部就绪。缓解：每屏诊断前先确认 `/api/products` 等端点 200，环境没起对就先起，不带着假信号硬跑。
- **9 屏工作量大，可能跨多个 session**：用 `.superpowers/sdd/progress.md` ledger（subagent-driven-development 标准做法）记录每屏完成状态，session 中断后可从 ledger 恢复，不重新诊断已完成的屏。
