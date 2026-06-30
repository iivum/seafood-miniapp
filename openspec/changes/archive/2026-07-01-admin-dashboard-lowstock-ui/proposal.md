# Proposal: admin-ui Dashboard 库存预警 UI 完整对齐（ad-02 LowStock）

## Why

后端 `GET /api/admin/dashboard` 已返回 `lowStock`（库存 < 10 的 Top 10 商品列表），
`DashboardPage.tsx` 也渲染了 `<LowStockList>` 组件，但**从未与 OD 设计稿（ad-02 仪表盘）
系统比对**：

1. OD 设计稿中库存预警区有明确的**红色警示标签**（`stock < 5` 深红 / `5≤stock<10` 橙色）
2. 每行商品展示**缩略图 + 名称 + 当前库存 + 「去补货」快捷链接**（跳转 ad-04 商品表单）
3. 当库存为 0 时显示**「已售罄」badge** 而非数字
4. 空态（库存充足）展示绿色「库存健康」插图

目前实现是否覆盖这 4 点未知，需要对比并补齐。

## What Changes

- `LowStockList` 组件：
  - 按库存分段着色（`stock === 0` → 售罄红 / `< 5` → 警告橙 / `< 10` → 提醒黄）
  - 每行加「去补货」按钮，`navigate` 到 `/products/${id}/edit`
  - 空态显示绿色健康状态
- `DashboardPage` 中 LowStock 区块标题、间距与 OD token 对齐
- 补充 Vitest 单元测试：3 段着色逻辑 + 空态渲染

## Capabilities

- **Modified Capabilities**：
  - `admin-ui/dashboard` — LowStockList 视觉完整对齐，补充库存分段色彩逻辑

## Impact

### 修改文件
- `admin-ui/src/features/dashboard/DashboardPage.tsx`（LowStockList 组件 + 区块布局）
- `admin-ui/src/features/dashboard/DashboardPage.test.tsx`（3 段着色 + 空态断言）

### 零后端改动
`lowStock` 字段已在 `DashboardResponse` 中，数据已有。

### 风险
- `lowStock` 后端返回的是 `ProductResponse`（含 `stock` 字段），需确认 stock 字段类型
  和空值处理（`null` 视为 0 还是跳过）
- 「去补货」跳转到 ProductForm 编辑页，需确认路由参数格式

### 前置依赖
- 无，可随时开始
