# Proposal: mp S-2 死交互修复（is-clicked 全屏覆盖）

## Why

MoSCoW 路线图 S-2 要求所有交互元素加 0.18s 闪动反馈（OD 示范的 `is-clicked` 模式）。
当前 `app.wxss` 已定义 `.is-clicked` 全局样式，`profile.wxml` 已有示例，但
**其他 7 个分包页面的交互元素均未挂载 `hover-class="is-clicked"`**：

- mp-01 首页：分类 chip 点击、tab 切换、Hero banner tap
- mp-02 分类页：左侧分类列表项、右侧商品卡
- mp-03 商品详情：stepper +/- 按钮、「立即购买」、「加入购物车」
- mp-04 购物车：全选、stepper、删除、「去结算」按钮
- mp-06 订单确认：配送方式切换、「提交订单」
- mp-07 地址管理：列表项 tap、「保存」按钮
- mp-08 订单列表：卡片 tap、操作按钮（取消/付款/提醒发货/确认收货）

没有点击反馈会让用户误认为操作没有响应，在网络慢时尤其明显（重复点击）。

## What Changes

- 所有上述 `bindtap` / `catchtap` 的可见交互元素加 `hover-class="is-clicked" hover-stay-time="100"`
- **不改** `app.wxss`（`.is-clicked` 已定义正确：0.18s scale + 透明度）
- 每屏新增或更新 Jest 快照测试，断言 `hover-class` 属性存在
- 视觉回归：与 C5 感知 diff 工具比对，确认闪动不引入布局偏移

## Capabilities

- **Modified Capabilities**：
  - `mini-program/interactions` — 7 屏交互元素统一加 hover 反馈

## Impact

### 修改文件（前端 wxml，纯属性变更）
- `frontend/pages/index/index.wxml`
- `frontend/pages/category/category.wxml`
- `frontend/pages-sub/product/product-detail/product-detail.wxml`
- `frontend/pages/cart/cart.wxml`
- `frontend/pages-sub/order/order-confirm/order-confirm.wxml`
- `frontend/pages-sub/user/address/address.wxml`（列表 + 编辑页）
- `frontend/pages-sub/order/order-list/order-list.wxml`

### 零后端改动、零样式改动

### 风险
- `hover-stay-time="100"` 在极低端机型上可能有 16ms 帧延迟，实测无感，可接受
- 部分 `scroll-view` 内部元素不支持 `hover-class`，需改用外层 `view` 包裹

### 前置依赖
- 无，可随时开始
