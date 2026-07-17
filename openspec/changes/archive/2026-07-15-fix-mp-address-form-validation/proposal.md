# Proposal: fix-mp-address-form-validation

## Why

2026-07-13 零 mock 全链路 E2E 实测:`pages-sub/address-edit` 的 `validateForm` 校验 `formData.province`,但 wxml 表单只产出 `region` 数组(无 `name="province"` 字段),全字段填满仍恒报「请选择所在地区」。真实用户永远无法创建收货地址 → 下单闭环实际不可达。**P0**:这是当前 mp 唯一挡死核心购买路径的缺陷。

## What Changes

- 修复 `pages-sub/address-edit` 表单校验:校验逻辑与表单实际产出字段对齐(以 `region` 数组为准,或表单同步产出 province/city/district 三字段,择一,以现有 `AddressUpsertRequest` 契约为锚)
- 补 Jest 单测锁死「表单 submit 产出的字段集 ⊆ validateForm 校验的字段集」这一契约,防止再次漂移
- E2E 验收:mp 运行时内真实走「填表单 → 保存 → 地址列表出现新地址 → 结算页可选中」全流程(接真后端,零 mock)

## Capabilities

- **New Capabilities**: 无
- **Modified Capabilities**:
  - `address-management`:补充地区选择字段的校验行为要求(表单产出 `region` 数组时,校验必须针对该数组而非不存在的独立字段)

## Impact

- 前端:`frontend/pages-sub/address-edit/`(js 校验逻辑,可能含 wxml 字段名)
- 后端:无(`POST /api/addresses` 契约不变,E2E 已实证后端链路可用)
- 测试:`frontend/pages-sub/address-edit/__tests__/` 新增;mp E2E 用例扩展
- 证据:E2E 报告 j5a-address-form-attempt.png;memory `mp-e2e-fullstack-2026-07-13`
