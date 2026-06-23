# admin-ui Orders OD 对齐实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ad-05 OrderList + ad-06 OrderDetail 与 OD 设计稿对齐：状态 tab、批量操作、3 列布局、物流时间线

**Architecture:** 纯前端修改，后端 API 已就位

**Tech Stack:** React 18, TypeScript strict, shadcn/ui, Vitest

## Global Constraints
- Order.tracking 为 null 时第 3 节点显示「等待签收」而非空
- 批量发货只对 PAID 状态订单可选
- 不改后端，不改 API，不新增依赖

---

## 文件变更一览

| 文件 | 操作 |
|---|---|
| `admin-ui/src/features/orders/OrderListPage.tsx` | 修改：STATUS_TABS 减到 5 个 + 空态 UI + loading spinner |
| `admin-ui/src/features/orders/OrderListPage.test.tsx` | 新增：tab 文案 / 空态 / spinner 断言 |
| `admin-ui/src/features/orders/OrderDetailPage.tsx` | 修改：操作 Card / header / 商品表格 / REFUNDING 区域 |
| `admin-ui/src/features/orders/OrderDetailPage.test.tsx` | 新增：REFUNDING 区域 / 订单号标签断言 |
| `admin-ui/src/features/orders/OrderTrackingTimeline.tsx` | 修改：delivered null 降级 + done 节点图标 |
| `admin-ui/src/features/orders/OrderTrackingTimeline.test.tsx` | 新增：tracking null 降级 / bg-success 断言 |

---

## Task 1：OrderListPage 状态 tab 对齐（5 tabs + 文案）

**Files:**
- Modify: `admin-ui/src/features/orders/OrderListPage.tsx`
- Test: `admin-ui/src/features/orders/OrderListPage.test.tsx`

**What changes:**
- `STATUS_TABS` 从 7 个减到 5 个（全部 / 待付款 / 待发货 / 已发货 / 已完成）
- PENDING label 从 "待支付" 改为 "待付款"；PAID label 从 "已付款" 改为 "待发货"
- 移除 CANCELLED / REFUNDING / REFUNDED 三个 tab（订单在 ALL 中仍可见）

---

- [ ] **Step 1.1：写 RED 测试——tab 文案与 OD 一致**

在 `OrderListPage.test.tsx` 的 `describe('OrderListPage')` 块末尾、最后一个 `}` 前添加：

```typescript
it('OD ad-05: 状态 tab 精确为 5 个（全部/待付款/待发货/已发货/已完成）', async () => {
  renderWithProviders(<OrderListPage />, { authenticated: true });
  await screen.findByText('o1'); // 等数据加载

  const tabs = screen.getAllByRole('tab');
  const labels = tabs.map((t) => t.textContent?.trim());
  expect(labels).toEqual(['全部', '待付款', '待发货', '已发货', '已完成']);
});

it('OD ad-05: 切到"待付款"tab 只显示 PENDING 订单', async () => {
  const user = userEvent.setup();
  renderWithProviders(<OrderListPage />, { authenticated: true });
  await screen.findByText('o1');
  await user.click(screen.getByRole('tab', { name: '待付款' }));
  await waitFor(() => {
    expect(screen.getByText('o3')).toBeInTheDocument(); // PENDING
    expect(screen.queryByText('o1')).not.toBeInTheDocument(); // PAID
    expect(screen.queryByText('o2')).not.toBeInTheDocument(); // SHIPPED
  });
});

it('OD ad-05: 切到"待发货"tab 只显示 PAID 订单', async () => {
  const user = userEvent.setup();
  renderWithProviders(<OrderListPage />, { authenticated: true });
  await screen.findByText('o1');
  await user.click(screen.getByRole('tab', { name: '待发货' }));
  await waitFor(() => {
    expect(screen.getByText('o1')).toBeInTheDocument(); // PAID
    expect(screen.queryByText('o2')).not.toBeInTheDocument(); // SHIPPED
    expect(screen.queryByText('o3')).not.toBeInTheDocument(); // PENDING
  });
});
```

运行确认 RED：
```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/admin-ui && npx vitest run src/features/orders/OrderListPage.test.tsx 2>&1 | tail -20
```

- [ ] **Step 1.2：修改 STATUS_TABS（GREEN）**

在 `OrderListPage.tsx` 中，将 `STATUS_TABS` 常量替换为：

```tsx
/** OD ad-05：5 状态 tab（全部/待付款/待发货/已发货/已完成）*/
const STATUS_TABS: { value: StatusTab; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待付款' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'COMPLETED', label: '已完成' },
];
```

同时删除 `StatusTab` 类型中不再需要的 tab（类型本身保留所有 `OrderStatusCode` 值，tab 渲染减少即可）。

- [ ] **Step 1.3：确认 GREEN + 现有测试不破坏**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/admin-ui && npx vitest run src/features/orders/OrderListPage.test.tsx 2>&1 | tail -20
```

注意：现有测试 `4.16:filters by status tab (PAID only)` 使用 `getByRole('tab', { name: '已付款' })`，此 tab 名已改为"待发货"，需同步更新该测试断言（name 改为 `'待发货'`）。

- [ ] **Step 1.4：commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp && git add admin-ui/src/features/orders/OrderListPage.tsx admin-ui/src/features/orders/OrderListPage.test.tsx && git commit -m "$(cat <<'EOF'
fix(admin-ui): OD ad-05 状态 tab 改为 5 个（待付款/待发货/已发货/已完成）

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 2：OrderListPage 批量操作 loading/disabled + 空态 UI

**Files:**
- Modify: `admin-ui/src/features/orders/OrderListPage.tsx`
- Test: `admin-ui/src/features/orders/OrderListPage.test.tsx`

**What changes:**
- 批量发货按钮 pending 时显示 `Loader2 animate-spin` 图标
- 导出 CSV 按钮 pending 时显示 `Loader2 animate-spin` 图标
- 空态从纯文本"暂无数据"改为 `Package` 图标 + "暂无订单" + 副文案

---

- [ ] **Step 2.1：写 RED 测试——空态 UI**

在 `OrderListPage.test.tsx` 末尾（Task 1 测试之后）添加：

```typescript
it('OD ad-05: 空态显示 Package 图标文案而非"暂无数据"', async () => {
  const user = userEvent.setup();
  // 切到 COMPLETED tab，sampleOrders 无 COMPLETED 数据 → 空态
  renderWithProviders(<OrderListPage />, { authenticated: true });
  await screen.findByText('o1');
  await user.click(screen.getByRole('tab', { name: '已完成' }));
  await waitFor(() => {
    expect(screen.getByText('暂无订单')).toBeInTheDocument();
    expect(screen.getByText('当前筛选条件下没有订单')).toBeInTheDocument();
    expect(screen.queryByText('暂无数据')).not.toBeInTheDocument();
  });
});

it('OD ad-05: 批量发货 pending 时显示 animate-spin 图标', async () => {
  // 让 batchShip 挂住不 resolve，验证 pending UI
  mockOrdersApi.batchShip.mockImplementation(() => new Promise(() => {}));
  const user = userEvent.setup();
  renderWithProviders(<OrderListPage />, { authenticated: true });
  await screen.findByText('o1');
  await user.click(screen.getByLabelText('选择订单 o1'));
  const batchBtn = await screen.findByRole('button', { name: /批量发货/ });
  await user.click(batchBtn);
  await waitFor(() => {
    const spinner = document.querySelector('.animate-spin');
    expect(spinner).toBeInTheDocument();
  });
});

it('OD ad-05: 导出 CSV pending 时显示 animate-spin 图标', async () => {
  mockOrdersApi.exportCsv.mockImplementation(() => new Promise(() => {}));
  const user = userEvent.setup();
  renderWithProviders(<OrderListPage />, { authenticated: true });
  await screen.findByText('o1');
  await user.click(screen.getByRole('button', { name: /导出 CSV/ }));
  await waitFor(() => {
    const spinner = document.querySelector('.animate-spin');
    expect(spinner).toBeInTheDocument();
  });
});
```

运行确认 RED：
```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/admin-ui && npx vitest run src/features/orders/OrderListPage.test.tsx 2>&1 | tail -20
```

- [ ] **Step 2.2：修改 OrderListPage.tsx——import 加 Loader2 + Package**

在文件顶部 import 行：
```tsx
// Before
import { ChevronRight, Download, Truck } from 'lucide-react';
// After
import { ChevronRight, Download, Loader2, Package, Truck } from 'lucide-react';
```

- [ ] **Step 2.3：修改批量发货按钮（GREEN）**

将批量发货 `<Button>` 内容替换：
```tsx
{batchShip.isPending ? (
  <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
) : (
  <Truck className="mr-1.5 h-4 w-4" />
)}
{batchShip.isPending ? '发货中' : '批量发货'}
```

- [ ] **Step 2.4：修改导出 CSV 按钮（GREEN）**

将导出按钮 `<Button>` 内容替换：
```tsx
{exportCsv.isPending ? (
  <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
) : (
  <Download className="mr-1.5 h-4 w-4" />
)}
导出 CSV
```

- [ ] **Step 2.5：修改空态 UI（GREEN）**

将 `filteredContent.length === 0` 分支的 `<TableRow>` 替换：
```tsx
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

- [ ] **Step 2.6：确认 GREEN**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/admin-ui && npx vitest run src/features/orders/OrderListPage.test.tsx 2>&1 | tail -20
```

- [ ] **Step 2.7：commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp && git add admin-ui/src/features/orders/OrderListPage.tsx admin-ui/src/features/orders/OrderListPage.test.tsx && git commit -m "$(cat <<'EOF'
fix(admin-ui): OD ad-05 空态 UI + 批量操作 loading spinner

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 3：OrderDetailPage 视觉对齐（操作区 / header / 商品列 / REFUNDING）

**Files:**
- Modify: `admin-ui/src/features/orders/OrderDetailPage.tsx`
- Test: `admin-ui/src/features/orders/OrderDetailPage.test.tsx`

**What changes:**
- 操作 Card 去掉 `CardTitle="操作"`，按钮直接在 `CardContent`
- header 订单号加 `"订单号："` 标签前缀
- 订单商品表格去掉"状态"列（4 列：商品 / 单价 / 数量 / 小计）
- REFUNDING 操作区从单一跳转按钮改为退款审核提示区域

---

- [ ] **Step 3.1：写 RED 测试——REFUNDING 审核区域 + 订单号标签**

在 `OrderDetailPage.test.tsx` 的 `describe` 块末尾添加：

```typescript
it('OD ad-06: header 显示"订单号："标签前缀', async () => {
  mockOrdersApi.detail.mockResolvedValue(paidDetail);
  renderDetailPage();
  expect(await screen.findByText(/订单号：/)).toBeInTheDocument();
});

it('OD ad-06: REFUNDING 操作区显示退款审核提示区域', async () => {
  const refundingDetail = {
    ...paidDetail,
    order: { ...paidDetail.order, status: 'REFUNDING' as const },
  };
  mockOrdersApi.detail.mockResolvedValue(refundingDetail);
  renderDetailPage();
  expect(await screen.findByText('退款审核中')).toBeInTheDocument();
  expect(screen.getByText('用户已申请退款，请前往退款管理审核')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /前往退款管理/ })).toBeInTheDocument();
  // 旧的"查看退款"按钮文案不应出现
  expect(screen.queryByRole('button', { name: /^查看退款$/ })).not.toBeInTheDocument();
});

it('OD ad-06: REFUNDED 操作区显示"查看退款记录"按钮', async () => {
  const refundedDetail = {
    ...paidDetail,
    order: { ...paidDetail.order, status: 'REFUNDED' as const },
  };
  mockOrdersApi.detail.mockResolvedValue(refundedDetail);
  renderDetailPage();
  expect(await screen.findByRole('button', { name: /查看退款记录/ })).toBeInTheDocument();
});

it('OD ad-06: 操作区无 CardTitle "操作"', async () => {
  mockOrdersApi.detail.mockResolvedValue(paidDetail);
  renderDetailPage();
  await screen.findByRole('link', { name: /返回.*列表/ });
  // 操作卡不应有标题 "操作"
  expect(screen.queryByRole('heading', { name: /^操作$/ })).not.toBeInTheDocument();
});
```

运行确认 RED：
```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/admin-ui && npx vitest run src/features/orders/OrderDetailPage.test.tsx 2>&1 | tail -20
```

- [ ] **Step 3.2：修改 header 订单号（GREEN）**

在 `OrderDetailPage.tsx` 中：
```tsx
// Before
<p className="font-mono text-sm text-muted">{order.id}</p>

// After
<p className="text-sm text-muted">
  订单号：<span className="font-mono">{order.id}</span>
</p>
```

- [ ] **Step 3.3：修改订单商品表格——去掉"状态"列（GREEN）**

移除 `<TableHead>状态</TableHead>` 及其对应 `<TableCell>` 的商品状态 Badge。表格从 5 列变为 4 列（商品 / 单价 / 数量 / 小计）。

同时移除 `CardDescription` 中的 import `CardDescription`（若仍用到则保留）。

- [ ] **Step 3.4：修改操作 Card——去掉 CardHeader/CardTitle（GREEN）**

```tsx
// Before
<Card>
  <CardHeader>
    <CardTitle>操作</CardTitle>
  </CardHeader>
  <CardContent className="space-y-2">
    ...buttons...
  </CardContent>
</Card>

// After
<Card>
  <CardContent className="space-y-2 pt-6">
    ...buttons...
  </CardContent>
</Card>
```

移除 `CardHeader` 的 import（若其他地方仍用则保留）。

- [ ] **Step 3.5：修改 REFUNDING/REFUNDED 操作区（GREEN）**

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

- [ ] **Step 3.6：确认 GREEN**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/admin-ui && npx vitest run src/features/orders/OrderDetailPage.test.tsx 2>&1 | tail -20
```

- [ ] **Step 3.7：commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp && git add admin-ui/src/features/orders/OrderDetailPage.tsx admin-ui/src/features/orders/OrderDetailPage.test.tsx && git commit -m "$(cat <<'EOF'
fix(admin-ui): OD ad-06 详情页操作区 + 订单号标签 + 商品列 + REFUNDING 区域

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## Task 4：OrderTrackingTimeline 样式 + null 降级（等待签收）

**Files:**
- Modify: `admin-ui/src/features/orders/OrderTrackingTimeline.tsx`
- Test: `admin-ui/src/features/orders/OrderTrackingTimeline.test.tsx`

**What changes:**
- `delivered` 节点未完成时显示"等待签收"副文案（`tracking=null` 或 events 不足时）
- `done=true` 节点图标从 `text-success Check` 改为填充圆（`bg-success` + 白色 Check）
- `done=false` 节点图标圆圈颜色从 `text-muted` 改为 `text-muted/40`（更弱）

---

- [ ] **Step 4.1：写 RED 测试——tracking null 降级 + done 节点背景**

在 `OrderTrackingTimeline.test.tsx` 的 `describe('OrderTrackingTimeline component')` 块末尾添加：

```typescript
it('OD: tracking=null + SHIPPED → delivered 节点显示"等待签收"副文案', () => {
  render(<OrderTrackingTimeline order={{
    ...base,
    status: 'SHIPPED',
    tracking: null,
  }} />);
  expect(screen.getByText('等待签收')).toBeInTheDocument();
});

it('OD: tracking=undefined + SHIPPED → delivered 节点显示"等待签收"', () => {
  const { tracking: _omit, ...orderWithoutTracking } = { ...base, status: 'SHIPPED' as const, tracking: undefined };
  render(<OrderTrackingTimeline order={orderWithoutTracking} />);
  expect(screen.getByText('等待签收')).toBeInTheDocument();
});

it('OD: COMPLETED + 3 events → done 节点有 bg-success class（填充圆）', () => {
  render(<OrderTrackingTimeline order={{
    ...base, status: 'COMPLETED',
    tracking: {
      carrier: '顺丰', trackingNumber: 'SF123',
      events: [
        { at: '2026-06-02T10:00:00Z', status: 'SHIPPED', location: '上海', description: '已发货' },
        { at: '2026-06-02T18:00:00Z', status: 'IN_TRANSIT', location: '杭州', description: '运输中' },
        { at: '2026-06-03T10:00:00Z', status: 'DELIVERED', location: '北京', description: '已签收' },
      ],
    },
  }} />);
  const successNodes = document.querySelectorAll('.bg-success');
  // COMPLETED 3 events: shipped + inTransit + delivered 全部 done → 3 个填充圆
  expect(successNodes.length).toBe(3);
});
```

运行确认 RED：
```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/admin-ui && npx vitest run src/features/orders/OrderTrackingTimeline.test.tsx 2>&1 | tail -20
```

- [ ] **Step 4.2：修改 OrderTrackingTimeline.tsx——节点图标样式（GREEN）**

将节点图标的 JSX 替换：
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

同时移除 import 中不再直接用于顶层的 `Circle`（如仍在同文件其他地方使用则保留）。

- [ ] **Step 4.3：修改节点内容——delivered null 降级（GREEN）**

将 `<li>` 内的 `<div className="flex-1">` 内部替换为：
```tsx
<div className="flex-1">
  <div className="text-sm font-medium">
    {NODE_LABEL[k]}
    {stage.at ? (
      <span className="ml-2 text-xs text-muted">
        {formatDateTime(stage.at)}
      </span>
    ) : null}
  </div>
  {k === 'delivered' && !stage.done ? (
    <p className="text-xs text-muted">等待签收</p>
  ) : null}
</div>
```

- [ ] **Step 4.4：确认 GREEN（含原有测试）**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/admin-ui && npx vitest run src/features/orders/OrderTrackingTimeline.test.tsx 2>&1 | tail -20
```

- [ ] **Step 4.5：全量测试——确认无回归**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/admin-ui && npx vitest run 2>&1 | tail -30
```

- [ ] **Step 4.6：commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp && git add admin-ui/src/features/orders/OrderTrackingTimeline.tsx admin-ui/src/features/orders/OrderTrackingTimeline.test.tsx && git commit -m "$(cat <<'EOF'
fix(admin-ui): OD 物流时间线 delivered null 降级"等待签收" + done 节点填充圆

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## 验收清单

完成所有 Task 后验证：

- [ ] `npx vitest run` 全绿（含 OrderListPage / OrderDetailPage / OrderTrackingTimeline 三个测试文件）
- [ ] OrderListPage 状态 tab 精确为 5 个，文案与 OD 一致（待付款 / 待发货 / 已发货 / 已完成）
- [ ] 切"已完成"tab 无数据 → 显示 Package 图标 + "暂无订单"
- [ ] 批量发货 pending → Loader2 spin；导出 CSV pending → Loader2 spin
- [ ] OrderDetailPage header 显示"订单号："前缀
- [ ] REFUNDING 操作区显示退款审核提示 + "前往退款管理"按钮
- [ ] REFUNDED 操作区显示"查看退款记录"按钮
- [ ] OrderTrackingTimeline：tracking=null 时 delivered 显示"等待签收"
- [ ] OrderTrackingTimeline：done 节点有 `bg-success` 填充圆
