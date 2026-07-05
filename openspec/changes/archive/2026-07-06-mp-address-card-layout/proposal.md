## Why

`mp-od-prototype-alignment`(已归档,2026-07-04)对齐 mp-07 地址列表页时,reviewer 发现地址卡片操作栏(设为默认/编辑/删除)是竖排侧边栏,而 OD 原型是卡片底部一条横向操作栏(虚线分隔上方地址内容)。当时判定不在该轮 brief 范围内(未触碰既有布局),记入遗留问题清单,持续拖累这一屏的感知 diff 分数。这是一个独立、范围很小的纯前端视觉/布局改动,现在单独修。

## What Changes

- `frontend/pages-sub/user/address/address-list.wxss` 的 `.address-card__actions`:`flex-direction: column` + 右侧 `border-left` 竖排侧边栏 → `flex-direction: row` + 卡片底部、上方 `border-top: 1rpx dashed` 虚线分隔,三个操作项(设为默认/编辑/删除)横向平铺
- `.address-card__action`:每项内部 icon+文字从"图标在上、文字在下"竖排 → icon 和文字同行水平排列(与 OD 一致)
- 不改变现有交互逻辑:「设为默认」仍只在 `!item.isDefault` 时显示(是否应该像 OD 一样对所有卡片都显示一个可切换的 radio,是本次改动范围外的交互设计问题,不在这次布局对齐里决定)

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `mini-program`:「Address management page (mp-07) OD-aligned layout」requirement 的地址卡片描述补一条明确的操作栏布局要求(横向底部操作行,而非当前的竖排侧边栏),让这条既有 requirement(此前只靠宽松的 5% 感知 diff 阈值间接约束)对操作栏方向/位置有可测试的显式描述

## Impact

- **前端**:`frontend/pages-sub/user/address/address-list.wxss`(`.address-card__actions`/`.address-card__action` 相关规则)、`frontend/pages-sub/user/address/address-list.wxml`(如需要调整 icon/文字的 DOM 顺序以配合新布局)
- 无 BREAKING:纯视觉改动,不改变任何 bindtap 目标、不改变任何数据结构或 API 调用
- 不涉及(OD 原型里出现但明确超出本次布局对齐范围的内容差异,几何层/感知层已有先例把这类差异归类为「架构性差异」,不阻塞):OD mockup 额外展示的冷链服务banner("已开通 12 城冷链")、每条地址的仓储/配送备注文案("海港冷链中心 3 号库 102 室·顺丰冷链可达")、地址标签徽章(DEFAULT/公司/家人)——这些都是当前 `Address` 域模型完全没有的字段,需要新增领域能力才能做,不是布局问题,建议另开 change
- 不涉及:「设为默认」在 OD 里对所有地址都显示为一个可切换 radio(含已默认地址),而当前实现只对非默认地址显示——这是交互行为差异,不是布局差异,本次不改
