## 1. 类型补全

- [x] 1.1 在 `DashboardPage.tsx` 的 `LowStockList` props inline type 中加 `imageUrl?: string | null`
- [x] 1.2 确认 `DashboardPage` 对接处 `data.lowStock` 已透传 `imageUrl`（后端 `ProductResponse` 已含该字段）

## 2. 缩略图列

- [x] 2.1 在 `LowStockList` 表格「商品」列，名称前插入 32×32 缩略图：`imageUrl` 有值 → `<img>`（`loading="lazy"` + `onError` 降级）；无值 → 灰色占位 div（`bg-muted`）
- [x] 2.2 调整「商品」`TableCell` 为 flex row，缩略图与商品名横排对齐

## 3. 补货链接修正

- [x] 3.1 将「去补货」`Link` 的 `to` 从 `/admin/products` 改为 `/admin/products?highlight=${p.id}`

## 4. 测试

- [x] 4.1 在 `DashboardPage.test.tsx` 补测试：有 `imageUrl` → 渲染 `<img>` 且 src 正确
- [x] 4.2 补测试：`imageUrl` 为 `null` → 渲染占位 div，不渲染 `<img>`
- [x] 4.3 补测试：「去补货」链接 href 含 `highlight=<id>`（用 `screen.getByRole('link', { name: '去补货' })`）
- [x] 4.4 运行 `cd admin-ui && npm test` 确认全部测试绿（118/118）

## 5. 提交

- [x] 5.1 `git add admin-ui/src/features/dashboard/DashboardPage.tsx admin-ui/src/features/dashboard/DashboardPage.test.tsx`
- [x] 5.2 `git commit -m "feat(admin): LowStockList 补缩略图列 + 去补货深链 + 着色测试"`
