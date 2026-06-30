## ADDED Requirements

### Requirement: Dashboard LowStockList 三段着色
仪表盘库存预警列表 `LowStockList` SHALL 按库存数量分三段着色：
- `stock === 0`：显示「已售罄」红色 badge（`text-destructive` / `bg-destructive/10` border），不显示数字
- `1 ≤ stock < 5`：数字显示为橙色（`text-orange-600 font-semibold`）
- `5 ≤ stock < 10`：数字显示为黄色（`text-yellow-600 font-medium`）

#### Scenario: stock=0 显示已售罄 badge
- **WHEN** `lowStock` 列表中某商品 `stock === 0`
- **THEN** 该行库存列渲染「已售罄」badge，class 含 `text-destructive`
- **AND** 不渲染数字 `0`

#### Scenario: stock 在 [1,5) 显示橙色
- **WHEN** `lowStock` 列表中某商品 `1 ≤ stock < 5`
- **THEN** 该行库存数字的 class 含 `text-orange-600`

#### Scenario: stock 在 [5,10) 显示黄色
- **WHEN** `lowStock` 列表中某商品 `5 ≤ stock < 10`
- **THEN** 该行库存数字的 class 含 `text-yellow-600`

---

### Requirement: Dashboard LowStockList 缩略图列
`LowStockList` 表格 SHALL 在「商品」列渲染 32×32 px 缩略图（`h-8 w-8 rounded object-cover`）：
- 若 `imageUrl` 有值：渲染 `<img src={imageUrl} loading="lazy">`，`onError` 降级到灰色占位
- 若 `imageUrl` 为 `null` 或空字符串：渲染灰色占位 div（`bg-muted`），不渲染 `<img>`

#### Scenario: 商品有 imageUrl 渲染缩略图
- **WHEN** `lowStock[i].imageUrl` 为非空字符串
- **THEN** 该行渲染 `<img>` 元素，`src` 等于 `imageUrl`，class 含 `rounded`

#### Scenario: 商品无 imageUrl 渲染灰色占位
- **WHEN** `lowStock[i].imageUrl` 为 `null` 或 `""`
- **THEN** 该行渲染灰色占位 div，class 含 `bg-muted`
- **AND** 不渲染 `<img>` 元素

---

### Requirement: Dashboard LowStockList 空态
`LowStockList` 在 `items.length === 0` 时 SHALL 渲染绿色空态，包含：
- 绿色圆形图标（`CheckCircle2`，`text-green-600`）
- 文字「库存健康」

#### Scenario: lowStock 为空数组
- **WHEN** `GET /api/admin/dashboard` 返回 `lowStock: []`
- **THEN** 仪表盘库存预警区域渲染「库存健康」文字
- **AND** 不渲染表格行

---

### Requirement: Dashboard LowStockList 补货链接
`LowStockList` 每行 SHALL 渲染「去补货」链接，目标为 `/admin/products?highlight=${id}`，
使商家可在商品列表中定位并处理低库存商品。

#### Scenario: 点击「去补货」导航到商品列表
- **WHEN** 用户点击某商品行的「去补货」链接
- **THEN** 浏览器导航到 `/admin/products?highlight=${该商品id}`
