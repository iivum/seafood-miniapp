# Proposal: admin-ui 订单模块 OD 对齐(ad-05 OrderList + ad-06 OrderDetail)

## Why

Sprint 2 OD 对齐（PR #32）覆盖了 Login / Dashboard / ProductForm 三个页面，但
**ad-05 OrderListPage** 和 **ad-06 OrderDetailPage** 尚未验证与 Open Design 设计稿的
一致性。两个页面的核心交互（批量发货、状态 tabs、3 列详情布局、物流时间线）是运营
日常高频操作，视觉/交互偏差会直接影响使用效率。

后端 API 已全部就位（`/api/admin/orders/batch-ship`、`/api/admin/orders/export`、
`/api/admin/orders/{id}/print-picklist`、`OrderTrackingTimeline`），前端代码也已
存在，但**从未系统地与 OD 设计稿逐元素比对**。

## What Changes

**ad-05 OrderListPage（订单列表）**：
- 对齐 OD 设计稿 5 个状态 tab（全部 / 待付款 / 待发货 / 已发货 / 已完成）的样式与激活态
- DataTable 列宽、字体、间距与 OD token 对齐
- 批量发货按钮 loading / disabled 状态视觉
- 导出 CSV 按钮位置与 OD 一致
- 空状态（无订单）插图与文案对齐

**ad-06 OrderDetailPage（订单详情）**：
- 3 列布局（订单商品 / 用户信息 / 金额明细）是否与 OD 一致
- `OrderTrackingTimeline` 3 节点（已下单 / 已发货 / 已签收）样式对齐
- 退款模块条件渲染（`REFUNDING` 状态时展示退款审核区域）
- 底部操作按钮区与 OD 对齐

**测试**：
- 每个修改点补充 Vitest 单元测试断言（渲染正确 class / 文案）
- e2e test 跑通"筛选已付款 → 查看详情 → 物流时间线可见"

## Capabilities

- **Modified Capabilities**：
  - `admin-ui/orders` — OrderListPage + OrderDetailPage + OrderTrackingTimeline 视觉对齐

## Impact

### 修改文件
- `admin-ui/src/features/orders/OrderListPage.tsx`
- `admin-ui/src/features/orders/OrderDetailPage.tsx`
- `admin-ui/src/features/orders/OrderTrackingTimeline.tsx`
- `admin-ui/src/features/orders/OrderListPage.test.tsx`
- `admin-ui/src/features/orders/OrderDetailPage.test.tsx`
- `admin-ui/src/features/orders/OrderTrackingTimeline.test.tsx`

### 零后端改动
已有全部 API，纯前端对齐。

### 风险
- OD 设计稿中物流时间线第 3 节点（已签收）数据来源依赖 `Order.tracking.deliveredAt`，
  后端暂时填 `null`（C-1 物流对接未做）；前端需做空值降级显示"等待签收"。

### 前置依赖
- OD 设计稿：`admin-ui` 对应 ad-05 / ad-06 屏的 HTML mockup（已在 Open Design 项目 `686e3434`）
- 无代码前置依赖
