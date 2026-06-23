# admin Dashboard LowStockList 完整实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现库存预警三段着色 + 售罄 badge + 去补货链接 + 空态，对齐 OD 设计

**Architecture:** 纯前端组件修改，DashboardPage.tsx 内 LowStockList 函数组件

**Tech Stack:** React 18, TypeScript strict, shadcn/ui, Vitest

## Global Constraints
- stock === 0 → destructive/red（sold-out badge：`bg-destructive/10 text-destructive border border-destructive/20`）
- 1 ≤ stock < 5 → 橙色（`text-orange-600 font-semibold`）
- 5 ≤ stock < 10 → 黄色（`text-yellow-600 font-medium`）
- 「去补货」链接用 react-router `<Link to="/admin/products">去补货</Link>`（非 `/products/${id}/edit`，因为编辑是弹窗而非独立路由）
- 不改后端，不改 API
- 不新增文件，仅修改现有两个文件

---

## Task 1：补 Vitest 测试（先写，先红）

**文件**: `admin-ui/src/features/dashboard/DashboardPage.test.tsx`

**操作**: 在现有 `describe('DashboardPage')` 内追加以下测试用例。

### 步骤

- [ ] 1.1 在 `DashboardPage.test.tsx` 末尾（`});` 前）插入 4 个新 `it` 块

```ts
it('LowStock: stock=0 显示已售罄 badge 而非数字', async () => {
  mockDashboard.get.mockResolvedValueOnce({
    orderStats: { today: 0, week: 0, month: 0, gmvToday: 0, avgOrderToday: 0 },
    productStats: { total: 0, onSale: 0, outOfStock: 0, byCategory: {} },
    topProducts: [],
    trend7d: [],
    recentOrders: [],
    lowStock: [
      { id: 'p-sold', name: '蛏子', description: '', price: '25.00', stock: 0,
        category: '贝类', imageUrl: '', status: 'ACTIVE', createdAt: '', updatedAt: '' },
    ],
  });
  renderWithProviders(<DashboardPage />, { authenticated: true });
  await waitFor(() => expect(screen.getByText('蛏子')).toBeInTheDocument());
  expect(screen.getByText('已售罄')).toBeInTheDocument();
  // 不应存在纯数字 "0" 作为库存显示
  expect(screen.queryByRole('cell', { name: '0' })).not.toBeInTheDocument();
});

it('LowStock: 1≤stock<5 显示橙色数字', async () => {
  mockDashboard.get.mockResolvedValueOnce({
    orderStats: { today: 0, week: 0, month: 0, gmvToday: 0, avgOrderToday: 0 },
    productStats: { total: 0, onSale: 0, outOfStock: 0, byCategory: {} },
    topProducts: [],
    trend7d: [],
    recentOrders: [],
    lowStock: [
      { id: 'p-orange', name: '扇贝', description: '', price: '38.00', stock: 3,
        category: '贝类', imageUrl: '', status: 'ACTIVE', createdAt: '', updatedAt: '' },
    ],
  });
  renderWithProviders(<DashboardPage />, { authenticated: true });
  await waitFor(() => expect(screen.getByText('扇贝')).toBeInTheDocument());
  const stockEl = screen.getByText('3');
  expect(stockEl).toHaveClass('text-orange-600');
});

it('LowStock: 5≤stock<10 显示黄色数字', async () => {
  mockDashboard.get.mockResolvedValueOnce({
    orderStats: { today: 0, week: 0, month: 0, gmvToday: 0, avgOrderToday: 0 },
    productStats: { total: 0, onSale: 0, outOfStock: 0, byCategory: {} },
    topProducts: [],
    trend7d: [],
    recentOrders: [],
    lowStock: [
      { id: 'p-yellow', name: '鲍鱼', description: '', price: '99.00', stock: 7,
        category: '贝类', imageUrl: '', status: 'ACTIVE', createdAt: '', updatedAt: '' },
    ],
  });
  renderWithProviders(<DashboardPage />, { authenticated: true });
  await waitFor(() => expect(screen.getByText('鲍鱼')).toBeInTheDocument());
  const stockEl = screen.getByText('7');
  expect(stockEl).toHaveClass('text-yellow-600');
});

it('LowStock: 空态显示库存健康图标', async () => {
  mockDashboard.get.mockResolvedValueOnce({
    orderStats: { today: 0, week: 0, month: 0, gmvToday: 0, avgOrderToday: 0 },
    productStats: { total: 0, onSale: 0, outOfStock: 0, byCategory: {} },
    topProducts: [],
    trend7d: [],
    recentOrders: [],
    lowStock: [],
  });
  renderWithProviders(<DashboardPage />, { authenticated: true });
  await waitFor(() => expect(screen.getByText('库存健康')).toBeInTheDocument());
  expect(screen.getByText('所有商品库存充足，无需补货')).toBeInTheDocument();
});

it('LowStock: 非零库存商品显示「去补货」链接', async () => {
  mockDashboard.get.mockResolvedValueOnce({
    orderStats: { today: 0, week: 0, month: 0, gmvToday: 0, avgOrderToday: 0 },
    productStats: { total: 0, onSale: 0, outOfStock: 0, byCategory: {} },
    topProducts: [],
    trend7d: [],
    recentOrders: [],
    lowStock: [
      { id: 'p-link', name: '海胆', description: '', price: '150.00', stock: 5,
        category: '海鲜', imageUrl: '', status: 'ACTIVE', createdAt: '', updatedAt: '' },
    ],
  });
  renderWithProviders(<DashboardPage />, { authenticated: true });
  await waitFor(() => expect(screen.getByText('海胆')).toBeInTheDocument());
  const link = screen.getByRole('link', { name: '去补货' });
  expect(link).toHaveAttribute('href', '/admin/products');
});
```

- [ ] 1.2 运行 `cd admin-ui && npx vitest run src/features/dashboard/DashboardPage.test.tsx`，确认 5 个新 case 全部 FAIL（因为实现还未做）
- [ ] 1.3 记录失败原因（预期：`已售罄` 找不到 / class 不匹配 / `库存健康` 找不到）

---

## Task 2：实现 LowStockList 着色逻辑 + badge + 链接

**文件**: `admin-ui/src/features/dashboard/DashboardPage.tsx`

### 步骤

- [ ] 2.1 在文件顶部 import 区添加 `CheckCircle2` 图标（lucide-react 已在项目中）：

```ts
import { CheckCircle2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
```

> 检查 `admin-ui/src/components/ui/badge.tsx` 是否存在，如不存在则直接用 `<span>` 实现 badge 样式。

- [ ] 2.2 替换 `LowStockList` 函数组件（L153-L193）：

```tsx
/* ---- 2.21 库存预警(stock < 10,Top 10)---- */
function LowStockList({
  items,
}: {
  items: { id: string; name: string; stock: number; category: string }[];
}) {
  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 py-8 text-center">
        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-green-50">
          <CheckCircle2 className="h-5 w-5 text-green-600" />
        </div>
        <p className="text-sm font-medium text-fg">库存健康</p>
        <p className="text-xs text-muted">所有商品库存充足，无需补货</p>
      </div>
    );
  }

  function stockColorClass(stock: number): string {
    if (stock === 0) return '';
    if (stock < 5) return 'text-orange-600 font-semibold';
    return 'text-yellow-600 font-medium';
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>商品</TableHead>
          <TableHead>分类</TableHead>
          <TableHead className="text-right">剩余库存</TableHead>
          <TableHead className="text-right">操作</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((p) => (
          <TableRow key={p.id}>
            <TableCell className="font-medium">{p.name}</TableCell>
            <TableCell className="text-muted">{p.category}</TableCell>
            <TableCell className="text-right">
              {p.stock === 0 ? (
                <span className="inline-flex items-center rounded-sm border border-destructive/20 bg-destructive/10 px-1.5 py-0.5 text-xs font-medium text-destructive">
                  已售罄
                </span>
              ) : (
                <span className={stockColorClass(p.stock)}>{p.stock}</span>
              )}
            </TableCell>
            <TableCell className="text-right">
              <Link
                to="/admin/products"
                className="text-xs text-accent hover:underline"
              >
                去补货
              </Link>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
```

- [ ] 2.3 运行 Vitest 确认 Task 1 写的 5 个 case 全部变绿：
```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/admin-ui && npx vitest run src/features/dashboard/DashboardPage.test.tsx
```

- [ ] 2.4 确认原有 2 个 case（`renders the dashboard with stats` / `shows error state`）未破坏

---

## Task 3：空态 UI 细化 + 全量测试

### 步骤

- [ ] 3.1 运行全量前端测试：
```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/admin-ui && npx vitest run
```

- [ ] 3.2 如有 TypeScript 类型错误，修正（预期无，因为接口形状未变）

- [ ] 3.3 检查 `lucide-react` 的 `CheckCircle2` 是否在现有 `package.json` 中（通常随 shadcn 安装）：
```bash
grep -r "lucide-react" /Users/linbinghui/agent-work/seafood-miniapp/admin-ui/package.json
```
  - 若未安装：改用 SVG inline 或使用已安装的其他 lucide 图标（如 `PackageCheck`、`ShieldCheck`）

- [ ] 3.4 运行 TypeScript 类型检查：
```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/admin-ui && npx tsc --noEmit
```

---

## Task 4：验收 + Commit

- [ ] 4.1 所有 Vitest 测试绿：`npx vitest run`（在 `admin-ui/` 目录）

- [ ] 4.2 视觉自查（可选）：若 dev server 可起，访问 `/admin/dashboard`，用 mock 数据确认三色渲染正常

- [ ] 4.3 git add 两个文件：
```bash
git add admin-ui/src/features/dashboard/DashboardPage.tsx
git add admin-ui/src/features/dashboard/DashboardPage.test.tsx
```

- [ ] 4.4 git commit：
```
feat(admin-ui): LowStockList 三段着色 + 售罄 badge + 去补货链接 + 空态

- stock=0: 「已售罄」badge (destructive 色系)
- 1≤stock<5: text-orange-600 数字
- 5≤stock<10: text-yellow-600 数字
- 「去补货」Link → /admin/products
- 空态: CheckCircle2 绿色「库存健康」图标

Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## 文件清单

| 文件 | 操作 | 备注 |
|---|---|---|
| `admin-ui/src/features/dashboard/DashboardPage.tsx` | 修改 | 替换 LowStockList 组件 |
| `admin-ui/src/features/dashboard/DashboardPage.test.tsx` | 修改 | 追加 5 个测试 case |

不新增文件，不改后端。
