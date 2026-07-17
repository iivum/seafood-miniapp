# Proposal: cleanup-mp-e2e-minor-findings

## Why

2026-07-13 零 mock E2E 顺带发现 2 个 Minor 问题:不挡链路,但一个是「守卫意图与实现脱节」的隐患,一个让 seed fixture 永远无效。集中一个小 change 清掉,避免散落遗忘。

## What Changes

1. **goToDetail 登录守卫死代码**:`pages/index` 与 `pages/category` 的 js 里存在带登录守卫的 `goToDetail`,但 wxml 实际用 `<navigator>` 直跳(`index.wxml:172` 附近),守卫从未执行。二选一并落 spec:
   - 若产品意图是「详情页需登录」→ wxml 改绑 `goToDetail`,守卫真实生效(注意:当前 E2E 实测未登录可看详情,改动前需产品确认);
   - 若意图是「详情页公开」→ 删除死代码守卫,消除误导。**默认取此项**(与现状行为一致,零行为变化)。
2. **seed 订单 fixture 孤儿化**:`backend/seed/fixtures/orders.json` 的 `userId=dev-user-001` 在 users 集合无对应用户,该订单对任何登录用户不可见(mp 按 userId 隔离,行为正确、fixture 无效)。修法:seed 脚本把订单 userId 动态对齐到 seed 的 customer 用户 `_id`(或文档化「订单 fixture 需运行时归属」,对齐 memory `c5-visual-test-runbook` 已有的 `seedOrdersFor` 实践)。

## Capabilities

- **New Capabilities**: 无
- **Modified Capabilities**:
  - `backend-test-fixtures`:补「订单 fixture 的 userId 必须归属 seed 用户集合中真实存在的用户」要求
  - `mini-program`:仅当选择「守卫生效」路线才有 spec 变化;默认删死代码路线无 spec 变化

## Impact

- 前端:`frontend/pages/index/`、`frontend/pages/category/`(删死代码或改 wxml 绑定)
- 后端:`backend/seed/seed.sh` + `fixtures/orders.json`
- 风险:低;守卫路线若选「生效」则是行为变化,需产品拍板后才实施
