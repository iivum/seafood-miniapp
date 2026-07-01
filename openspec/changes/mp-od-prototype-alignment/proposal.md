## Why

用户人工验收发现：微信小程序 9 个屏几乎全部与 Open Design（OD）原型存在明显样式/布局偏离，且伴随多处功能性 bug（如地址页 403、下单确认页空购物车直达）。

追溯根因：C5 视觉验证 change（`archive/2026-06-22-sprint-5-c5-visual-verification`）在 2026-06-22 归档时，9 屏的最终 baseline 全部为 RED（home 61% / category 66% / detail 67% / cart 55% / profile 71% / confirm 35% / address 38% / order-list 29% / order-detail 30% 感知偏离），仅 home 一屏被几何层驱动修到 GREEN。归档时把「逐屏修复」列为下游 backlog，此后再无人认领，问题一直悬着到今天用户人工发现。

工具本身是有效的（RED 结果准确反映了偏离），缺的是"照着 RED 结果把 9 屏逐个修完"这道工序。这次变更就是补上它。

## What Changes

- 按业务关键路径顺序（home → category → product-detail → cart → order-confirm → address → order-list → order-detail → profile）逐屏执行「重新诊断 → 派 subagent 修复 → 复验 GREEN」闭环
- 每屏诊断阶段重跑 `test:visual` + `test:geometry` 拿当前真实偏离数据（C5 数据已过时一周，过去一周有登录改造/hover-class/banner 接入等改动）
- 每屏修复不仅对齐样式/布局，顺带修复过程中发现的功能性 bug（历史经验：几何层诊断经常顺带挖出真 bug，如 C5 当年的 `isWxFail` 误判、dangling require）
- 在 `openspec/specs/mini-program/spec.md` 为当前缺失「OD-aligned layout」requirement 的 5 屏（mp-05 profile / mp-06 order-confirm / mp-07 address / mp-08 order-list 完整布局 / mp-09 order-detail）补齐 requirement，格式仿照已有的 mp-01~04
- 顺手把 mp-01~04 requirement 里过时的验证方式描述（"miniprogram-automator screenshot + haiku image comparison"）更新为现用的 odiff 感知层 + 几何层机制

## Capabilities

### New Capabilities
（无——不引入新能力域，是对既有 `mini-program` 能力查漏补缺）

### Modified Capabilities
- `mini-program`：新增 mp-05/06/07/08/09 的 OD-aligned layout requirement；更新 mp-01~04 requirement 中的验证方式描述

## Impact

- **前端**：`frontend/pages/{index,category,cart,profile}/*` + `frontend/pages-sub/{product/product-detail,order/order-confirm,order/order-list,order/order-detail,user/address}/*`（wxml/wxss/js 三件套，按屏改动范围而定）
- **后端**：诊断阶段如挖出真 bug（如 mp-07 地址 403 若未解决）会连带修复对应 Controller/Service
- **测试**：`frontend/e2e/tools/{visual-diff,geometry-diff}.cjs` 现有 harness 复用，不改工具本身；`openspec/specs/mini-program/spec.md` 增补 5 条 requirement
- **依赖**：需要本地 WeChat DevTools 自动化端口 + 后端（`./gradlew bootRun`,非过期 jvm 镜像）+ MongoDB + seed 数据全部就绪才能诊断
