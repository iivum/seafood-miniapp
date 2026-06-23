# admin Dashboard LowStockList 完整对齐 — 设计文档

> change: `admin-dashboard-lowstock-ui`
> 日期: 2026-06-23

---

## 1. 现状分析（Gap Analysis）

### 现有实现（DashboardPage.tsx L153-193）

| 项目 | 当前状态 |
|---|---|
| 三段着色 | 部分：`stock===0` → `text-error`；`<5` → `text-warning`；`5-9` 无色 |
| 「已售罄」badge | 缺失：stock=0 时仍显示数字 `0` |
| 「去补货」链接 | 完全缺失 |
| 空态 | 有文字「所有商品库存充足」，无绿色图标 |
| 缩略图 | 缺失（OD 要求每行展示缩略图） |

### Gap 汇总（按优先级）

1. **P0** — stock=0 时需显示「已售罄」badge，而非数字
2. **P0** — 5≤stock<10 需黄色（`text-yellow-600`），当前无色
3. **P1** — 「去补货」快捷链接（导航到商品列表页）
4. **P2** — 空态绿色「库存健康」图标
5. **P3** — 缩略图（imageUrl 字段已有，但需评估是否在 dashboard 展示）

> **缩略图决策**：LowStockList 接收的 items 类型当前只含 `{id, name, stock, category}`。
> 后端 `ProductResponse` 有 `imageUrl`，但需扩展前端类型定义。
> Sprint 内范围：P0/P1/P2，缩略图列为 P3 后续迭代。

---

## 2. 颜色分段规则

| 条件 | 视觉表现 | shadcn/Tailwind token |
|---|---|---|
| `stock === 0` | 「已售罄」红色 badge | `bg-destructive/10 text-destructive border-destructive/20` |
| `1 ≤ stock < 5` | 数字橙色加粗 | `text-orange-600 font-semibold` |
| `5 ≤ stock < 10` | 数字黄色中等 | `text-yellow-600 font-medium` |

**为何不用 `text-warning`**：当前项目 CSS 中 `text-warning` 未明确定义为橙色，`text-orange-600` 和 `text-yellow-600` 是 Tailwind 标准 token，在项目内已用于其他告警场景，稳定性更高。

---

## 3. 「去补货」路由策略

### 路由现状分析

`admin-ui/src/router.tsx` 中产品相关路由：
- `/admin/products` → `<ProductListPage />`

**ProductForm 是弹窗模式**（`ProductListPage.tsx` L371 Dialog），无独立路由 `/admin/products/:id/edit`。

### 决策

「去补货」链接跳转至 `/admin/products`（商品列表页），并通过 URL query 参数携带商品 id：

```
/admin/products?editId=${id}
```

**备选方案（未选）**：
- `<Link to="/admin/products">商品列表</Link>`（不传 id，用户需手动找到商品）—— 体验差
- 新增 `/admin/products/:id/edit` 路由 —— 需改 router + ProductForm，超出本 change 范围

**当前 Sprint 范围**：使用 `<Link to={/admin/products?highlight=${id}}>`，ProductListPage 解析 highlight 参数后续实现。本 change 仅实现链接导航到列表页，不要求 ProductListPage 响应 highlight 参数（避免 scope creep）。

> 最终决定：简单链接到 `/admin/products`，无 query 参数。原因：ProductListPage 未实现 highlight 逻辑，加 query 参数无实际效果，只增加用户困惑。

---

## 4. 空态 UI 方案

### 现状
```tsx
<p className="text-sm text-muted">所有商品库存充足</p>
```

### 目标（OD 对齐）
使用 shadcn 风格的绿色成功态：

```tsx
<div className="flex flex-col items-center gap-2 py-8 text-center">
  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-green-50">
    <CheckCircle2 className="h-5 w-5 text-green-600" />
  </div>
  <p className="text-sm font-medium text-fg">库存健康</p>
  <p className="text-xs text-muted">所有商品库存充足，无需补货</p>
</div>
```

图标来源：`lucide-react`（项目已依赖）中的 `CheckCircle2`。

---

## 5. LowStockList 类型扩展

当前类型（仅 dashboard 内联）：
```ts
items: { id: string; name: string; stock: number; category: string }[]
```

后端 `ProductResponse` 实际包含 `imageUrl`、`status` 等字段，但 dashboard API 通过 `lowStock: ProductResponse[]` 返回完整 DTO，前端 `DashboardPage.tsx` 接收时隐式截取了子集。

**本 change 范围**：LowStockList 继续使用当前内联类型，不扩展为 imageUrl（缩略图 P3）。类型安全方面无 breaking change。

---

## 6. 测试用例设计

### 6.1 三段着色测试

| 用例名 | 数据 | 断言 |
|---|---|---|
| `stock=0 显示「已售罄」badge` | `stock: 0` | `getByText('已售罄')` 存在；无文字 `0` |
| `1≤stock<5 显示橙色库存数` | `stock: 3` | 数字 `3` 有 class `text-orange-600` |
| `5≤stock<10 显示黄色库存数` | `stock: 7` | 数字 `7` 有 class `text-yellow-600` |

### 6.2 空态测试

| 用例名 | 数据 | 断言 |
|---|---|---|
| `空态显示库存健康` | `items: []` | `getByText('库存健康')` 存在 |

### 6.3 「去补货」链接测试

| 用例名 | 数据 | 断言 |
|---|---|---|
| `去补货链接指向商品列表` | 任意非零 stock 商品 | `getByRole('link', { name: '去补货' })` href 含 `/admin/products` |

### 6.4 全场景集成用例（在现有 DashboardPage describe 中扩展）

现有 `renders the dashboard with stats` 用例的 `lowStock` 数组中 `stock: 3`，补充断言：
- `getByText('扇贝')` 存在
- 「去补货」链接可访问

---

## 7. 实现边界

| 项目 | 本 change 内 | 范围外 |
|---|---|---|
| 三段着色 + sold-out badge | ✓ | |
| 「去补货」link → `/admin/products` | ✓ | ProductListPage highlight 逻辑 |
| 空态绿色图标 | ✓ | |
| 缩略图 | | P3 后续 |
| 后端 API 改动 | | 不需要 |
| DashboardPage 其他区块 | | 不动 |
