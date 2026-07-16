# Proposal: fix-category-bad-status-500

## Why

2026-07-13 E2E 实测:products 集合中单条文档的 `status` 为非枚举值(实测 `INACTIVE`,合法枚举仅 `ACTIVE/OUT_OF_STOCK/DISCONTINUED`)时,`findByCategory` 在 Mongo→实体反序列化阶段直接抛 `IllegalArgumentException: No enum constant`,**该分类整体 500 不可用**(实测 5 类目挂 3 个)。`ProductService.java` 的 setStatus 兜底在反序列化之后,来不及生效。单条坏数据放大成整个类目故障,爆炸半径不可接受。

## What Changes

- 商品读路径对非法/缺失 `status` 容错:反序列化层(converter 或 Document 用 String 承载 + mapper 收敛)把未知值归一到确定枚举(建议 `DISCONTINUED`,不出现在公共列表),坏文档降级为「该条不可见」而非「整查询抛异常」
- 公共分类查询(`findByCategory` 及同路径)与 `listPublic` 行为对齐:仅返回 `ACTIVE`,天然绕开坏数据
- 补测试:① 仓库层 IT——集合中混入非法 status 文档时,分类查询仍 200 且返回其余商品;② 领域层单测锁枚举归一规则
- 数据治理:`backend/seed/seed.sh` / fixtures 补合法 `status` 字段,消除已知坏数据来源(与 memory `c5-visual-test-runbook` 记录的 seed 坑闭环)

## Capabilities

- **New Capabilities**: 无
- **Modified Capabilities**:
  - `product-sku`:补充「持久化层出现非法 status 值时,读路径必须容错降级,禁止单条坏数据导致整个查询失败」的健壮性要求

## Impact

- 后端:`product/infra`(Document/converter/mapper)、`product/application`(查询过滤)、`backend/seed/`
- API:`GET /api/products?category=...` 行为从「坏数据即 500」变为「容错返回」;无契约字段变化
- 关联:HTTP 表象(403 空 body)由 `fix-error-contract-denyall` 独立修复,本 change 只管「不再抛」
- 复现:`curl 'localhost:8080/api/products?category=%E9%B1%BC%E7%B1%BB'`(需先注入一条 status=INACTIVE 的商品)
