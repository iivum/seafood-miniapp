# Admin-UI v2.2 OD Alignment 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 8 个 RED 测试(`ad-od-design.test.tsx`),将 LoginPage / DashboardPage / ProductForm 三个组件对齐 OD mockup,同时向后端增加 GMV 聚合端点,并将 admin-ui 覆盖率门加入 pre-commit hook。

**Architecture:**
- 后端新增 `OrderService.sumTotalAmountCreatedSince`(内存聚合,与 `findRecent` 同模式);`OrderStatsResponse` 扩字段;BFF `dashboard()` 计算 gmvToday + avgOrderToday。
- 前端 `types/api.ts` 跟随后端字段;`LoginPage` / `ProductForm` 最小改动通过文本 RED 测试;`DashboardPage` 重组 4 KPI 卡 + 加「导出报表」按钮。
- `.githooks/pre-commit` 追加 admin-ui 覆盖率检查。

**Tech Stack:** Java 25 / Spring Boot 4.0.6 / GraalVM / Mockito / AssertJ / React 18 / shadcn-ui / Vitest / TypeScript strict

## Global Constraints

- DDD 分层不可越:`bff` 只调 `OrderService` / `ProductService` / `UserService`(ApplicationService),不可直接碰 Repository
- `OrderService.sumTotalAmountCreatedSince` 遵循 `findRecent` 内存聚合模式(findTop500 + filter + reduce)
- TypeScript strict — 禁止 `any`
- 所有 admin-ui 文本 selector 均为 `anyMatch`(case-insensitive, `|` 分隔),添加字符串即可通过
- CONVERSION 指标展示占位 `"—"`,不引入访客追踪
- `导出报表` 按钮 UI-only,点击显示 toast

---

## 文件变更一览

| 文件 | 操作 |
|---|---|
| `backend/src/main/java/com/seafood/order/application/OrderService.java` | 新增方法 |
| `backend/src/test/java/com/seafood/order/application/OrderServiceTest.java` | 新增 2 个测试 |
| `backend/src/main/java/com/seafood/bff/admin/dto/OrderStatsResponse.java` | 扩字段 |
| `backend/src/main/java/com/seafood/bff/admin/AdminBffService.java` | 修改 `dashboard()` + 新增 import |
| `backend/src/test/java/com/seafood/bff/admin/AdminBffDashboardSliceTest.java` | 4 测试补 mock + 新增 1 测试 |
| `admin-ui/src/types/api.ts` | 扩 `OrderStatsResponse` 接口 |
| `admin-ui/src/features/auth/LoginPage.tsx` | 文本三处修改 |
| `admin-ui/src/features/products/ProductForm.tsx` | `submitLabel` 加默认值 |
| `admin-ui/src/features/dashboard/DashboardPage.tsx` | KPI 卡重组 + 导出按钮 |
| `.githooks/pre-commit` | 追加 admin-ui 覆盖率块 |

---

## Task 1: 后端 OrderService — 新增 GMV 聚合方法

**Files:**
- Modify: `backend/src/main/java/com/seafood/order/application/OrderService.java:444-446`
- Test: `backend/src/test/java/com/seafood/order/application/OrderServiceTest.java`

**Interfaces:**
- Produces: `public BigDecimal sumTotalAmountCreatedSince(Instant from)` — Task 2 的 BffService 将调用此方法

---

- [ ] **Step 1: 写 RED 测试(OrderServiceTest.java)**

在 `OrderServiceTest.java` 末尾、最后一个 `}` 之前添加以下两个测试方法:

```java
@Test
void sumTotalAmountCreatedSince_returnsZeroWhenNoOrders() {
    when(orderRepo.findTop500ByOrderByCreatedAtDesc()).thenReturn(List.of());

    BigDecimal result = service.sumTotalAmountCreatedSince(Instant.parse("2026-01-01T11:00:00Z"));

    assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
}

@Test
void sumTotalAmountCreatedSince_sumsOnlyOrdersInRange() {
    Instant base = Instant.parse("2026-01-01T12:00:00Z");
    Instant from = base.minusSeconds(3600); // 1 h 前

    // findTop500 按 createdAt 降序返回 — 最新在前
    OrderDocument o1 = new OrderDocument();
    o1.setCreatedAt(base.minusSeconds(100));
    o1.setTotalAmount(new BigDecimal("100.00"));

    OrderDocument o2 = new OrderDocument();
    o2.setCreatedAt(base.minusSeconds(600));
    o2.setTotalAmount(new BigDecimal("50.00"));

    OrderDocument o3 = new OrderDocument(); // 超出 1h 窗口
    o3.setCreatedAt(base.minusSeconds(7200));
    o3.setTotalAmount(new BigDecimal("999.00"));

    when(orderRepo.findTop500ByOrderByCreatedAtDesc()).thenReturn(List.of(o1, o2, o3));

    BigDecimal result = service.sumTotalAmountCreatedSince(from);

    assertThat(result).isEqualByComparingTo("150.00");
}
```

- [ ] **Step 2: 验证测试 FAIL**

```bash
cd backend
./gradlew :test --tests "*OrderServiceTest.sumTotalAmountCreatedSince*" -PexcludeTags=docker,native
```

预期输出: `FAILED` — `sumTotalAmountCreatedSince` 方法不存在(`cannot find symbol`)

- [ ] **Step 3: 实现方法**

在 `OrderService.java` `countCreatedSince` 方法(第 444-446 行)之后插入:

```java
public BigDecimal sumTotalAmountCreatedSince(Instant from) {
    return orders.findTop500ByOrderByCreatedAtDesc().stream()
            .takeWhile(doc -> !doc.getCreatedAt().isBefore(from))
            .map(OrderDocument::getTotalAmount)
            .filter(amount -> amount != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

确保文件顶部已有 `import java.math.BigDecimal;`(与 `totalAmount` 字段同包,通常已存在)。若无则在其他 `java.math.*` import 旁添加。

- [ ] **Step 4: 验证测试 PASS**

```bash
cd backend
./gradlew :test --tests "*OrderServiceTest.sumTotalAmountCreatedSince*" -PexcludeTags=docker,native
```

预期: `BUILD SUCCESSFUL`, 2 tests passed

- [ ] **Step 5: 全量后端测试**

```bash
cd backend
./gradlew check -PexcludeTags=docker,native
```

预期: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/seafood/order/application/OrderService.java \
        backend/src/test/java/com/seafood/order/application/OrderServiceTest.java
git commit -m "feat(order): 新增 sumTotalAmountCreatedSince — 内存聚合今日 GMV

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: 后端 DTO + BffService + BffService 测试

**Files:**
- Modify: `backend/src/main/java/com/seafood/bff/admin/dto/OrderStatsResponse.java`
- Modify: `backend/src/main/java/com/seafood/bff/admin/AdminBffService.java`
- Modify: `backend/src/test/java/com/seafood/bff/admin/AdminBffDashboardSliceTest.java`

**Interfaces:**
- Consumes: `OrderService.sumTotalAmountCreatedSince(Instant)` from Task 1
- Produces: `OrderStatsResponse` 新增 `gmvToday` / `avgOrderToday` 字段 — Task 3 的 `types/api.ts` 与之对应

---

- [ ] **Step 1: 更新 OrderStatsResponse.java**

将整个文件替换为:

```java
package com.seafood.bff.admin.dto;

import java.math.BigDecimal;

/** 订单统计 — 今日/本周/本月数量 + 今日 GMV / 客单价。 */
public record OrderStatsResponse(
        long today,
        long week,
        long month,
        BigDecimal gmvToday,
        BigDecimal avgOrderToday
) {
}
```

- [ ] **Step 2: 更新 AdminBffService.java**

2a. 在现有 import 块(第 18-29 行)中添加两行 import,紧跟其他 `java.*` import 之后:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
```

2b. 将 `dashboard()` 方法中的 `OrderStatsResponse orderStats = ...` 块(第 107-110 行)替换为:

```java
        long todayCount = orders.countCreatedSince(startOfToday);
        BigDecimal gmvToday = orders.sumTotalAmountCreatedSince(startOfToday);
        BigDecimal avgOrderToday = todayCount > 0
                ? gmvToday.divide(BigDecimal.valueOf(todayCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        OrderStatsResponse orderStats = new OrderStatsResponse(
                todayCount,
                orders.countCreatedSince(startOfWeek),
                orders.countCreatedSince(startOfMonth),
                gmvToday,
                avgOrderToday);
```

- [ ] **Step 3: 验证编译**

```bash
cd backend
./gradlew :compileJava
```

预期: `BUILD SUCCESSFUL`

- [ ] **Step 4: 更新 AdminBffDashboardSliceTest.java — 补 mock**

在 4 个现有测试方法中,每个 `when(orderService.countCreatedSince(any())).thenReturn(...)` 行之后紧接添加:

```java
when(orderService.sumTotalAmountCreatedSince(any())).thenReturn(BigDecimal.ZERO);
```

具体 4 处位置:
- `dashboard_topProducts_emptyOrders_returnsEmpty`:第 53 行之后
- `dashboard_topProducts_skipsMissingProduct`:第 69 行之后
- `dashboard_trend7d_computes7Points`:第 83 行之后
- `dashboard_lowStock_returnsFromQueryService`:第 101 行之后

- [ ] **Step 5: 新增 GMV 断言测试**

在文件末尾、最后一个 `}` 之前添加:

```java
@Test
void dashboard_orderStats_includesGmvAndAvgOrder() {
    when(orderService.findRecent(500)).thenReturn(List.of());
    when(orderService.findRecent(10)).thenReturn(List.of());
    when(orderService.countCreatedSince(any())).thenReturn(2L);
    when(orderService.sumTotalAmountCreatedSince(any())).thenReturn(new BigDecimal("200.00"));
    when(productQueryService.stats()).thenReturn(
        new ProductStatsResponse(0L, 0L, 0L, Map.of()));
    when(productQueryService.lowStock(10)).thenReturn(List.of());

    DashboardResponse resp = bffService.dashboard();

    assertThat(resp.orderStats().gmvToday()).isEqualByComparingTo("200.00");
    assertThat(resp.orderStats().avgOrderToday()).isEqualByComparingTo("100.00"); // 200 / 2
}
```

- [ ] **Step 6: 验证测试 PASS**

```bash
cd backend
./gradlew :test --tests "*AdminBffDashboardSliceTest*" -PexcludeTags=docker,native
```

预期: `BUILD SUCCESSFUL`, 5 tests passed

- [ ] **Step 7: 全量后端 check**

```bash
cd backend
./gradlew check -PexcludeTags=docker,native
```

预期: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/seafood/bff/admin/dto/OrderStatsResponse.java \
        backend/src/main/java/com/seafood/bff/admin/AdminBffService.java \
        backend/src/test/java/com/seafood/bff/admin/AdminBffDashboardSliceTest.java
git commit -m "feat(bff): dashboard 新增 gmvToday / avgOrderToday — OD ad-02 KPI 对齐

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 3: 前端 — types + LoginPage + ProductForm 小修

**Files:**
- Modify: `admin-ui/src/types/api.ts`
- Modify: `admin-ui/src/features/auth/LoginPage.tsx`
- Modify: `admin-ui/src/features/products/ProductForm.tsx`
- Test: `admin-ui/src/__tests__/ad-od-design.test.tsx` (只是跑,不修改)

**Interfaces:**
- Consumes: `OrderStatsResponse.gmvToday` / `avgOrderToday` from Task 2

---

- [ ] **Step 1: 跑 RED 测试确认当前失败数**

```bash
cd admin-ui
npm test -- --testPathPattern="ad-od-design" --no-coverage 2>&1 | grep -E "PASS|FAIL|✓|✗|×"
```

预期: 8 tests fail (ad-01 × 3, ad-02 × 4, ad-04 × 1)

- [ ] **Step 2: 更新 types/api.ts**

找到 `export interface OrderStatsResponse` 块(约第 176-180 行):

```typescript
export interface OrderStatsResponse {
  today: number;
  week: number;
  month: number;
}
```

替换为:

```typescript
export interface OrderStatsResponse {
  today: number;
  week: number;
  month: number;
  gmvToday: number;
  avgOrderToday: number;
}
```

- [ ] **Step 3: 修改 LoginPage.tsx — 三处文本**

3a. 第 145 行,将:
```tsx
<CardTitle>海鲜商城管理后台</CardTitle>
```
改为:
```tsx
<CardTitle>欢迎回来</CardTitle>
```

3b. 第 194 行,将:
```
记住我(下次自动填用户名)
```
改为:
```
7 天内免登录
```

3c. 第 183-196 行现有的 checkbox div:
```tsx
{/* 2.14:记住我(checkbox 状态受控 watch,勾选变更触发 username 持久化) */}
<div className="flex items-center gap-2">
  <Checkbox
    id="remember"
    checked={Boolean(remember)}
    onCheckedChange={(v) => setValue('remember', Boolean(v))}
    disabled={isLocked}
  />
  <Label
    htmlFor="remember"
    className="cursor-pointer text-sm text-muted"
  >
    7 天内免登录
  </Label>
</div>
```

替换为(在 Label 同一行级别后面追加「忘记密码？」链接):
```tsx
{/* 2.14:记住我(checkbox 状态受控 watch,勾选变更触发 username 持久化) */}
<div className="flex items-center justify-between">
  <div className="flex items-center gap-2">
    <Checkbox
      id="remember"
      checked={Boolean(remember)}
      onCheckedChange={(v) => setValue('remember', Boolean(v))}
      disabled={isLocked}
    />
    <Label
      htmlFor="remember"
      className="cursor-pointer text-sm text-muted"
    >
      7 天内免登录
    </Label>
  </div>
  <button
    type="button"
    className="text-sm text-muted underline-offset-2 hover:underline"
    onClick={() => {/* 忘记密码功能开发中 */}}
  >
    忘记密码？
  </button>
</div>
```

- [ ] **Step 4: 修改 ProductForm.tsx — submitLabel 加默认值**

第 65 行:
```typescript
  submitLabel: string;
```
改为:
```typescript
  submitLabel?: string;
```

第 68 行:
```typescript
export function ProductForm({ defaultValues, onSubmit, onCancel, submitting, submitLabel }: ProductFormProps) {
```
改为:
```typescript
export function ProductForm({ defaultValues, onSubmit, onCancel, submitting, submitLabel = '创建' }: ProductFormProps) {
```

- [ ] **Step 5: 验证 ad-01 和 ad-04 测试通过**

```bash
cd admin-ui
npm test -- --testPathPattern="ad-od-design" --no-coverage 2>&1 | grep -E "PASS|FAIL|ad-01|ad-04"
```

预期: ad-01 全 PASS(6/6), ad-04 全 PASS(2/2), ad-02 仍有 4 FAIL

- [ ] **Step 6: TypeScript 类型检查**

```bash
cd admin-ui
npx tsc --noEmit 2>&1 | head -20
```

预期: 0 错误

- [ ] **Step 7: Commit**

```bash
git add admin-ui/src/types/api.ts \
        admin-ui/src/features/auth/LoginPage.tsx \
        admin-ui/src/features/products/ProductForm.tsx
git commit -m "fix(admin-ui): OD 对齐 LoginPage 欢迎回来/7天免登录/忘记密码 + ProductForm 默认标签

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 4: 前端 DashboardPage — KPI 卡重组 + 导出报表按钮

**Files:**
- Modify: `admin-ui/src/features/dashboard/DashboardPage.tsx`

**Interfaces:**
- Consumes: `data.orderStats.gmvToday` / `data.orderStats.avgOrderToday` (Task 2+3 已对齐)

---

- [ ] **Step 1: 添加 useToast import**

在 `DashboardPage.tsx` 顶部 import 块末尾添加:

```typescript
import { useToast } from '@/components/ui/toaster';
```

- [ ] **Step 2: 在 DashboardPage 组件内获取 toast**

在 `DashboardPage` 组件函数体内(紧跟 `useQuery` hook 之后)添加:

```tsx
const toast = useToast();
```

- [ ] **Step 3: 替换页面 header 区域**

找到页面 header 区域(第 235-238 行):

```tsx
      <div>
        <h1 className="text-2xl font-semibold">仪表盘</h1>
        <p className="text-sm text-muted">订单、商品、销量概览</p>
      </div>
```

替换为:

```tsx
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">仪表盘</h1>
          <p className="text-sm text-muted">订单、商品、销量概览</p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => toast.show('导出报表功能开发中')}
        >
          导出报表
        </Button>
      </div>
```

- [ ] **Step 4: 替换 4 个 KPI 卡**

找到 KPI 卡区域(第 240-250 行):

```tsx
      {/* 2.19 4 KPI Card */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="今日订单" value={data.orderStats.today} hint="UTC+8 当日 0 点至今" />
        <StatCard label="本周订单" value={data.orderStats.week} hint="本周一 0 点至今" />
        <StatCard label="本月订单" value={data.orderStats.month} hint="本月 1 号 0 点至今" />
        <StatCard
          label="在售商品"
          value={data.productStats.onSale}
          hint={`共 ${data.productStats.total} 款 · 缺货 ${data.productStats.outOfStock}`}
        />
      </div>
```

替换为:

```tsx
      {/* OD ad-02: TODAY·GMV / ORDERS / AVG ORDER / CONVERSION */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="GMV 今日" value={formatPrice(data.orderStats.gmvToday)} hint="UTC+8 当日销售额" />
        <StatCard label="ORDERS 今日" value={data.orderStats.today} hint="UTC+8 当日 0 点至今" />
        <StatCard label="AVG ORDER 客单价" value={formatPrice(data.orderStats.avgOrderToday)} hint="今日 GMV / 今日订单数" />
        <StatCard label="CONVERSION 转化率" value="—" hint="需访客数据,功能开发中" />
      </div>
```

- [ ] **Step 5: 验证 ad-02 RED 测试全部通过**

```bash
cd admin-ui
npm test -- --testPathPattern="ad-od-design" --no-coverage 2>&1 | grep -E "PASS|FAIL"
```

预期: `PASS` — 全部 8 个 RED 测试均变绿

- [ ] **Step 6: 验证 ad-od-design 完整测试输出**

```bash
cd admin-ui
npm test -- --testPathPattern="ad-od-design" --no-coverage --verbose 2>&1 | tail -30
```

预期: 所有测试 `✓` 或 `PASS`,0 failures

- [ ] **Step 7: TypeScript 类型检查**

```bash
cd admin-ui
npx tsc --noEmit 2>&1 | head -20
```

预期: 0 错误

- [ ] **Step 8: admin-ui 全量测试 + 覆盖率**

```bash
cd admin-ui
npm run test:coverage 2>&1 | tail -20
```

预期: `All suites passed`, 覆盖率 ≥ 门槛(branches 75% / functions 80% / lines 80% / statements 80%)

- [ ] **Step 9: Commit**

```bash
git add admin-ui/src/features/dashboard/DashboardPage.tsx
git commit -m "feat(admin-ui): DashboardPage OD 对齐 — GMV/ORDERS/AVG ORDER/CONVERSION + 导出报表

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 5: Quality — pre-commit 加入 admin-ui 覆盖率检查

**Files:**
- Modify: `.githooks/pre-commit`

---

- [ ] **Step 1: 更新 pre-commit hook**

将 `.githooks/pre-commit` 整个文件替换为:

```bash
#!/usr/bin/env bash
set -euo pipefail

# GraalVM plugin 把 native-image-agent 无条件绑定到 :test，非 GraalVM JDK 会报错。
# Homebrew GraalVM 路径；若安装在其他位置请修改此处。
GRAALVM_HOME_BREW="/opt/homebrew/opt/graalvm/libexec/graalvm.jdk/Contents/Home"
if [ -d "$GRAALVM_HOME_BREW" ]; then
  export JAVA_HOME="$GRAALVM_HOME_BREW"
fi

REPO=$(git rev-parse --show-toplevel)

echo "▶ pre-commit: 后端 check (excludeTags=docker,native)…"
cd "$REPO/backend"
./gradlew --no-daemon check -PexcludeTags=docker,native
echo "✅ 后端 check 通过"

echo "▶ pre-commit: 前端单测…"
cd "$REPO/frontend"
TZ=UTC npm test -- --passWithNoTests
echo "✅ 前端单测通过"

echo "▶ pre-commit: admin-ui 单测 + 覆盖率门…"
cd "$REPO/admin-ui"
npm run test:coverage
echo "✅ admin-ui 覆盖率门通过"
```

- [ ] **Step 2: 验证 hook 可执行**

```bash
chmod +x /Users/linbinghui/agent-work/seafood-miniapp/.githooks/pre-commit
bash -n /Users/linbinghui/agent-work/seafood-miniapp/.githooks/pre-commit && echo "语法 OK"
```

预期: `语法 OK`

- [ ] **Step 3: 手动触发 admin-ui 块验证**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp/admin-ui
npm run test:coverage 2>&1 | tail -5
```

预期: `All suites passed` 且无覆盖率门失败

- [ ] **Step 4: Commit**

```bash
git add .githooks/pre-commit
git commit -m "chore(quality): pre-commit 追加 admin-ui 覆盖率门

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 验收清单(所有任务完成后)

- [ ] `cd backend && ./gradlew check -PexcludeTags=docker,native` — `BUILD SUCCESSFUL`
- [ ] `cd admin-ui && npm test -- --testPathPattern="ad-od-design" --no-coverage` — 全 8 个测试 PASS
- [ ] `cd admin-ui && npm run test:coverage` — 全部覆盖率门 ≥ 阈值
- [ ] `cd admin-ui && npx tsc --noEmit` — 0 错误
- [ ] pre-commit hook 触发 admin-ui 块无报错

---

## 常见坑

| 坑 | 解 |
|---|---|
| `AdminBffDashboardSliceTest` NPE on `gmvToday` | 4 个旧测试漏加 `when(orderService.sumTotalAmountCreatedSince(any())).thenReturn(BigDecimal.ZERO)` mock |
| `DashboardPage` TS 错误 `gmvToday does not exist` | `types/api.ts` 未更新 `OrderStatsResponse` |
| `formatPrice` 显示 `¥0.00` 而非 `—` | CONVERSION 卡传的是字符串字面量 `"—"`,不走 `formatPrice` — 正确 |
| `submitLabel` 仍是必填报错 | 需同时改接口 `?: string` 和解构默认值 `= '创建'` |
| `findTop500ByOrderByCreatedAtDesc` mock 丢失 | `AdminBffDashboardSliceTest` 内 `findRecent(500)` 和 `findRecent(10)` 均需 mock |
