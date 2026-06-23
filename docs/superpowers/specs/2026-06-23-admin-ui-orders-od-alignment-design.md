# Admin-UI Orders OD 对齐设计文档

> **Change:** `admin-ui-orders-od-alignment`
> **Scope:** ad-05 OrderListPage + ad-06 OrderDetailPage + OrderTrackingTimeline
> **Date:** 2026-06-23

---

## 1. Gap 分析

### 1.1 OrderListPage（ad-05）

| Gap 项 | 当前实现 | OD 应有 | 严重度 |
|---|---|---|---|
| 状态 tab 数量不对 | 7 tabs：全部/待支付/已付款/已发货/退款中/已完成/已取消 | 5 tabs：全部/待付款/待发货/已发货/已完成 | Major |
| tab 文案不对 | "待支付" → 应为 "待付款"；"已付款" 对 | "待付款"/"待发货" | Major |
| 空态 UI 缺文案 | `暂无数据`（纯文本，无图标/插图） | 需要图标 + 说明文案（"暂无订单"）| Minor |
| 批量发货按钮 loading 状态 | `batchShip.isPending ? '发货中...' : '批量发货'`，但按钮宽度未固定 | loading 中显示 spinner 图标 + 禁用态 | Minor |
| 导出 CSV 按钮 disabled 态 | `disabled={exportCsv.isPending}` 有，但无 loading 文案/图标 | loading 时应显示旋转图标 | Minor |
| tab `PAID` 文案 | 当前 tab value=PAID label=已付款 | label 应为"待发货"（已付款=等待发货）| Major |
| 描述文案 | "所有订单一览，点击行查看详情" | 无 — OD 无副标题 | Cosmetic |

**核心问题**：STATUS_TABS 定义有 7 个，OD 只有 5 个；且 "待付款"/"待发货" 两个 label 都映射到 PENDING/PAID，当前只有 "待支付"/"已付款"，文案不完全对齐。

**5 tab 目标**：

| Tab Value | OD Label |
|---|---|
| ALL | 全部 |
| PENDING | 待付款 |
| PAID | 待发货 |
| SHIPPED | 已发货 |
| COMPLETED | 已完成 |

移除 CANCELLED / REFUNDING / REFUNDED 三个 tab（这些订单仍在 ALL 中可见）。

### 1.2 OrderListPage 空态

| 当前 | 目标 |
|---|---|
| `<TableCell colSpan={8} className="text-center text-muted">暂无数据</TableCell>` | 独立空态组件：Package 图标 + "暂无订单" 标题 + "当前筛选条件下没有订单" 副文案 |

### 1.3 批量操作按钮状态

| 状态 | 当前行为 | 目标行为 |
|---|---|---|
| 未选中 | 按钮不显示 | 不显示（正确） |
| 选中 + 空闲 | 显示"批量发货" + Truck 图标 | 正确 |
| 选中 + pending | 显示"发货中..."文字，无 spinner | 显示 Loader2 spin 图标 + "发货中" |
| 导出空闲 | 显示"导出 CSV" | 正确 |
| 导出 pending | disabled 但无 spinner | 显示 Loader2 spin 图标 |

### 1.4 OrderDetailPage（ad-06）

| Gap 项 | 当前实现 | OD 应有 | 严重度 |
|---|---|---|---|
| 3 列布局 | `grid-cols-1 lg:grid-cols-3` 已实现 | 正确，无 gap | - |
| 金额明细 | 商品总额 / 配送费 / 实付，配送费硬编码 "—" | 正确（等后端配送费字段） | - |
| 订单商品列 "状态" | 有 "状态" 列（显示商品上下架） | OD 无商品状态列，多余 | Minor |
| REFUNDING 条件渲染 | `order.status === 'REFUNDING' \| 'REFUNDED'` 显示"查看退款" | OD 要求 REFUNDING 时在操作区显示退款审核区域，不只是跳链接 | Major |
| 底部操作 Card | CardTitle="操作" + 按钮列表 | OD 操作区无 CardTitle，按钮直接在 card content | Minor |
| header 描述文案 | `text-muted` 的 `{order.id}` | OD header 有"订单号:"标签前缀 | Minor |

### 1.5 OrderTrackingTimeline

| Gap 项 | 当前实现 | OD 应有 | 严重度 |
|---|---|---|---|
| `tracking = null` 时节点标签 | 3 节点均显示，但时间戳为空 | `delivered` 节点 `at=null` 时应显示"等待签收"副文案 | Major |
| 节点图标 done 状态 | `Check` 绿色 + `Circle` 灰色 | 已完成节点圆圈用 `bg-success` 填充 + 白色 check；未完成用空心圆圈 | Minor |
| `tracking = null` 时整体显示 | `shouldShowTimeline` 返 true（status=SHIPPED），timeline Card 显示，但 3 节点全部 done=false | 需在 `tracking=null` 时节点显示"等待签收"而非空 | Major |
| carrier + trackingNumber 文案 | `{carrier} · 单号 {trackingNumber}` | 正确 | - |

### 1.6 `tracking = null` 降级方案

当 `order.tracking` 为 null（C-1 物流对接未完成）：

- `shipped` 节点：`done=false`，显示"等待发货"副文案
- `inTransit` 节点：`done=false`，无副文案
- `delivered` 节点：`done=false`，显示**"等待签收"**副文案（OD 硬性要求）

当 `order.tracking.events` 为空数组（已有 tracking 对象但无事件）：

- `shipped` 节点：`done=false`，无时间
- 其余同上

---

## 2. 修改点明细

### 2.1 OrderListPage.tsx

**变更 1：STATUS_TABS 减到 5 个**

```tsx
// Before (7 tabs)
const STATUS_TABS: { value: StatusTab; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待支付' },
  { value: 'PAID', label: '已付款' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'REFUNDING', label: '退款中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
];

// After (5 tabs — OD ad-05)
const STATUS_TABS: { value: StatusTab; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待付款' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'COMPLETED', label: '已完成' },
];
```

**变更 2：空态 UI（`filteredContent.length === 0` 分支）**

```tsx
// Before
<TableRow>
  <TableCell colSpan={8} className="text-center text-muted">
    暂无数据
  </TableCell>
</TableRow>

// After
<TableRow>
  <TableCell colSpan={8}>
    <div className="flex flex-col items-center gap-2 py-12 text-muted">
      <Package className="h-10 w-10 opacity-40" />
      <p className="font-medium">暂无订单</p>
      <p className="text-xs">当前筛选条件下没有订单</p>
    </div>
  </TableCell>
</TableRow>
```

需要在 import 中加 `Package` from `lucide-react`。

**变更 3：批量发货按钮 loading**

```tsx
// Before
<Truck className="mr-1.5 h-4 w-4" />
{batchShip.isPending ? '发货中...' : '批量发货'}

// After
{batchShip.isPending ? (
  <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
) : (
  <Truck className="mr-1.5 h-4 w-4" />
)}
{batchShip.isPending ? '发货中' : '批量发货'}
```

**变更 4：导出 CSV 按钮 loading**

```tsx
// Before
<Download className="mr-1.5 h-4 w-4" />
导出 CSV

// After
{exportCsv.isPending ? (
  <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
) : (
  <Download className="mr-1.5 h-4 w-4" />
)}
导出 CSV
```

需要在 import 中加 `Loader2` from `lucide-react`。

### 2.2 OrderDetailPage.tsx

**变更 1：操作 Card 去掉 CardTitle**

```tsx
// Before
<Card>
  <CardHeader>
    <CardTitle>操作</CardTitle>
  </CardHeader>
  <CardContent className="space-y-2">
    ...
  </CardContent>
</Card>

// After
<Card>
  <CardContent className="space-y-2 pt-6">
    ...
  </CardContent>
</Card>
```

**变更 2：header 订单号加标签前缀**

```tsx
// Before
<p className="font-mono text-sm text-muted">{order.id}</p>

// After
<p className="text-sm text-muted">
  订单号：<span className="font-mono">{order.id}</span>
</p>
```

**变更 3：订单商品表格去掉"状态"列**

移除 `<TableHead>状态</TableHead>` 及对应 `<TableCell>` 中的商品状态 Badge，colSpan 从 5 改为 4。

**变更 4：REFUNDING 状态在操作区显示退款审核提示**

```tsx
// Before
{order.status === 'REFUNDING' || order.status === 'REFUNDED' ? (
  <Button variant="outline" className="w-full" onClick={() => navigate('/admin/refunds')}>
    <Undo2 className="mr-2 h-4 w-4" />
    查看退款
  </Button>
) : null}

// After
{order.status === 'REFUNDING' ? (
  <div className="rounded-md border border-warning/40 bg-warning/5 p-3 text-sm">
    <p className="font-medium text-warning">退款审核中</p>
    <p className="mt-1 text-muted">用户已申请退款，请前往退款管理审核</p>
    <Button
      variant="outline"
      size="sm"
      className="mt-2 w-full"
      onClick={() => navigate('/admin/refunds')}
    >
      <Undo2 className="mr-1.5 h-4 w-4" />
      前往退款管理
    </Button>
  </div>
) : null}
{order.status === 'REFUNDED' ? (
  <Button variant="outline" className="w-full" onClick={() => navigate('/admin/refunds')}>
    <Undo2 className="mr-2 h-4 w-4" />
    查看退款记录
  </Button>
) : null}
```

### 2.3 OrderTrackingTimeline.tsx

**变更 1：`delivered` 节点 null 降级**

在 `<div className="flex-1">` 的内部，节点文案下方加副文案：

```tsx
// delivered 节点：tracking 为 null 或 done=false 时显示"等待签收"
<div className="flex-1">
  <div className="text-sm font-medium">
    {NODE_LABEL[k]}
    {stage.at ? (
      <span className="ml-2 text-xs text-muted">{formatDateTime(stage.at)}</span>
    ) : null}
  </div>
  {/* tracking null 降级：delivered 未完成时显示等待提示 */}
  {k === 'delivered' && !stage.done ? (
    <p className="text-xs text-muted">等待签收</p>
  ) : null}
</div>
```

**变更 2：done 节点图标样式（视觉增强）**

```tsx
// Before
{stage.done ? (
  <Check className="mt-0.5 h-4 w-4 shrink-0 text-success" />
) : (
  <Circle className="mt-0.5 h-4 w-4 shrink-0 text-muted" />
)}

// After
{stage.done ? (
  <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-success">
    <Check className="h-3 w-3 text-white" />
  </span>
) : (
  <Circle className="mt-0.5 h-5 w-5 shrink-0 text-muted/40" />
)}
```

---

## 3. 测试断言补充

### 3.1 OrderListPage.test.tsx 新增断言

- **tab 文案验证**：tab "待付款" / "待发货" / "已发货" / "已完成" 各 `getByRole('tab')`
- **空态验证**：切到 COMPLETED tab 且无数据 → 出现 "暂无订单" 文案
- **批量发货 loading spinner**：mock batchShip 为 pending → 按钮内有 `animate-spin` class
- **导出 CSV loading spinner**：mock exportCsv 为 pending → 按钮内有 `animate-spin` class

### 3.2 OrderDetailPage.test.tsx 新增断言

- **REFUNDING 操作区**：order.status=REFUNDING → 显示"退款审核中"文案 + "前往退款管理"按钮
- **订单号标签**：渲染 `订单号：` 前缀

### 3.3 OrderTrackingTimeline.test.tsx 新增断言

- **tracking null + SHIPPED**：`delivered` 节点显示"等待签收"副文案
- **done 节点背景**：`bg-success` class 在 done=true 节点存在

---

## 4. 不改动项

- 后端 API：无变更
- OrderDetailPage 3 列布局：已正确
- OrderDetailPage 金额明细：已正确
- timeline.ts 算法：无变更（降级在 UI 层处理）
- 现有测试：不破坏，只新增断言
