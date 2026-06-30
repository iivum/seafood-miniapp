# Design: admin-ui Dashboard 库存预警 UI 完整对齐（ad-02 LowStock）

## Context

后端 `GET /api/admin/dashboard` 经 BFF `AdminBffService` 返回 `DashboardResponse.lowStock`，
类型为 `List<ProductResponse>`，含 `id / name / stock / category / imageUrl / status` 等全量字段。

前端 `DashboardPage.tsx` 的 `LowStockList` 组件已实现：
- 空态绿色「库存健康」插图 ✅
- 「已售罄」badge（stock === 0）✅
- 三段着色（0 → badge / <5 → orange / <10 → yellow）✅
- 「去补货」链接（当前目标：`/admin/products` 列表页）⚠️
- Vitest 测试：3 段着色 + 空态 ✅

**缺口：**
1. `imageUrl` 字段存在于后端 ProductResponse 但前端 props type 未声明,列未渲染缩略图
2. 「去补货」当前跳列表页而非商品编辑表单（ad-04 路由 `/admin/products/:id/edit` 尚不存在）

## Goals / Non-Goals

**Goals:**
- 为 `LowStockList` props 加 `imageUrl?: string`,渲染 32×32 缩略图（有图显示/无图占位）
- 「去补货」链接改为深链到 `/admin/products?highlight=${id}`,在列表页高亮该商品（无需新路由）
- DashboardPage 把 `imageUrl` 传入 `LowStockList`
- 更新 Vitest 测试：缩略图有/无 + 「去补货」链接带正确 query

**Non-Goals:**
- 新建 `/admin/products/:id/edit` 单独编辑路由（Sprint 2+ 的 ad-04 范围）
- 后端改动（ProductResponse 已含 imageUrl，DashboardResponse 无需改）
- 商品状态颜色标签以外的样式大改（只补 imageUrl 列）

## Decisions

### D1: 缩略图列——有/无图双态

ProductResponse.imageUrl 可能为 `null`/空字符串（未上传图片的商品）。
- 有图：`<img src={imageUrl} className="h-8 w-8 rounded object-cover" />`
- 无图：灰色占位 `<div className="h-8 w-8 rounded bg-muted flex items-center justify-center">…</div>`
- 备选方案：不加缩略图列（跳过）→ 拒绝，OD 设计稿明确要求

### D2: 「去补货」跳转目标——高亮 query 参数 vs 直接编辑页

**选择：`/admin/products?highlight=${id}`（高亮 query）**
理由：`/admin/products/:id/edit` 路由当前不存在，创建完整编辑页超出本 change 范围；
高亮 query 是最小可行跳转，ProductListPage 未来可消费 `highlight` param（可选渐进增强）。
- 备选：保持 `/admin/products`（现状）→ 不改，无助于用户定位该商品

### D3: LowStockList props type 扩展

现有 inline type：
```ts
items: { id: string; name: string; stock: number; category: string }[]
```
扩展为：
```ts
items: { id: string; name: string; stock: number; category: string; imageUrl?: string | null }[]
```
调用处 `DashboardPage` 对接 `data.lowStock`，后端已有 imageUrl，直接透传，零类型断言。

## Risks / Trade-offs

- [风险] ProductListPage 未消费 `?highlight` query → 跳转有效但无高亮视觉
  → 缓解：链接本身是最小有效跳转,可日后增强;不影响本 change 正确性
- [风险] imageUrl 为 CDN 外链，img 跨域或加载慢
  → 缓解：加 `loading="lazy"` + `onError` 降级为占位
- [Trade-off] 不建 edit 路由 → 「去补货」体验次优,但在 ad-04 落地前这是最小侵入方案
