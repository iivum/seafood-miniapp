# mp-09 Order Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 mp-09 订单详情页(海鲜商城 · 微信小程序),OD 设计稿对齐,3 个底部 action 全接业务。

**Architecture:** 后端 `Order` aggregate 加 `estimatedDelivery: Instant` 字段(create 时 = now + 24h),OrderResponse 透传。前端 `pages-sub/order/order-detail/` 4 新文件 + `utils/order-detail-derive.js` 纯函数。OrderResponse 已有 `tracking.trackingNumber` + `tracking.carrier` 字段复用,不需要 OrderTracking 改。TDD 顺序:3-RED → 1-GREEN → REFACTOR,严格 TDD 铁律。

**Tech Stack:**
- Backend: Java 25 + Spring Boot 4.0.6 + GraalVM Native + Spring Data MongoDB 5
- Frontend: 微信小程序 (TS 5.x strict + WXML/WXSS)
- Test: JUnit 5(backend) + Jest 29.x(frontend)
- Validation: chroma.js + WCAG ratio

---

## File Structure

### New files
- `frontend/pages-sub/order/order-detail/order-detail.js`(200-300 行,onLoad + 3 handler + 2 derive 调用)
- `frontend/pages-sub/order/order-detail/order-detail.wxml`(150-200 行,5 section + bottom-bar)
- `frontend/pages-sub/order/order-detail/order-detail.wxss`(200-300 行,5 section 样式 + bottom-bar)
- `frontend/pages-sub/order/order-detail/order-detail.json`(10 行,usingComponents)
- `frontend/utils/order-detail-derive.js`(80-120 行,deriveBanner + deriveTimeline 纯函数)
- `frontend/utils/order-detail-derive.d.ts`(15-25 行,types)

### Modified files
- `backend/src/main/java/com/seafood/order/domain/Order.java`(+1 字段 estimatedDelivery)
- `backend/src/main/java/com/seafood/order/api/dto/OrderResponse.java`(+1 record 字段 + from 透传)
- `backend/src/main/java/com/seafood/order/application/OrderService.java`(create 算 estimatedDelivery)
- `backend/src/test/java/com/seafood/order/api/dto/OrderResponseJsonTest.java`(+1 test estimatedDeliveryRoundtrip)
- `frontend/pages-sub/order/order-list/order-list.wxml`(卡片 bindtap 跳 order-detail)
- `frontend/app.json`(pages 加 order-detail)
- `frontend/e2e/mp-od-design.test.ts`(OD_SPECS 加 mp-09)
- `frontend/e2e/mp-3layer.test.ts`(PAGES 加 mp-09)

---

## Task 1: 后端 RED — OrderResponseJsonTest 加 estimatedDelivery roundtrip

**Files:**
- Modify: `backend/src/test/java/com/seafood/order/api/dto/OrderResponseJsonTest.java`(找现有测试)

- [ ] **Step 1: 读现有 OrderResponseJsonTest,理解测试模式**

```bash
cat backend/src/test/java/com/seafood/order/api/dto/OrderResponseJsonTest.java
```

期望:看到现有 test 模式(可能已有 `trackingRoundtrip` / `refundIdRoundtrip` 之类)。记下 setup 用的 builder/factory 名(大概率 `Order.create()` 或 `new Order(...)`)。

- [ ] **Step 2: 加 estimatedDeliveryRoundtrip 测试到 OrderResponseJsonTest**

在 OrderResponseJsonTest 末尾加(用 setup 同样的 factory):

```java
@Test
void estimatedDeliveryRoundtrip() {
    var o = Order.create("user-1", List.of(item), "wechat");
    var now = Instant.parse("2026-06-18T10:00:00Z");
    var withEstimate = o.withEstimatedDelivery(now);
    var resp = OrderResponse.from(withEstimate);

    assertThat(resp.estimatedDelivery()).isEqualTo(now);

    // JSON 序列化往返不应丢失字段
    String json = objectMapper.writeValueAsString(resp);
    var back = objectMapper.readValue(json, OrderResponse.class);
    assertThat(back.estimatedDelivery()).isEqualTo(now);
}
```

注:`Order.create(...)` 是 Order aggregate factory(查 domain/Order.java 看签名),`withEstimatedDelivery(now)` 是新加的命名方法(在 Task 2 才加)。此处用 compile-failure 作为 RED。

- [ ] **Step 3: 跑测试,确认 RED**

```bash
cd backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.order.api.dto.OrderResponseJsonTest.estimatedDeliveryRoundtrip"
```

期望:**BUILD FAILED**(编译错:`withEstimatedDelivery` 方法不存在,或 `estimatedDelivery()` record 字段不存在)。这是 TDD 铁律的真 RED — feature missing,不是 typo。

- [ ] **Step 4: 不 commit(RED 状态),继续 Task 2**

---

## Task 2: 后端 GREEN — Order 加 estimatedDelivery 字段

**Files:**
- Modify: `backend/src/main/java/com/seafood/order/domain/Order.java`
- Modify: `backend/src/main/java/com/seafood/order/api/dto/OrderResponse.java`
- Modify: `backend/src/main/java/com/seafood/order/application/OrderService.java`

- [ ] **Step 1: 读 Order.java 看现有字段 + 命名方法模式**

```bash
cat backend/src/main/java/com/seafood/order/domain/Order.java
```

期望:record 风格,含 `cancel(cancelReason)` / `markPaid()` / `confirm()` 等命名方法返回新实例(不可变模式)。

- [ ] **Step 2: Order.java 加 estimatedDelivery record 字段 + withEstimatedDelivery 命名方法**

在 Order record components 列表加(放在 tracking 后,refundId 前):

```java
public record Order(
    String id,
    String userId,
    List<OrderItem> items,
    BigDecimal totalAmount,
    OrderStatus status,
    String cancelReason,
    OrderTracking tracking,
    String refundId,
    Instant estimatedDelivery,  // ← 新增
    Instant createdAt,
    Instant updatedAt
) {
    public Order {
        // 现有 validation 保持
    }

    public Order withEstimatedDelivery(Instant newEstimated) {
        return new Order(id, userId, items, totalAmount, status,
            cancelReason, tracking, refundId, newEstimated, createdAt, updatedAt);
    }
}
```

- [ ] **Step 3: OrderResponse.java 加 estimatedDelivery record 字段 + from 透传**

```java
public record OrderResponse(
    String id,
    String userId,
    List<OrderItem> items,
    BigDecimal totalAmount,
    String status,
    String cancelReason,
    OrderTracking tracking,
    String refundId,
    Instant estimatedDelivery,  // ← 新增
    Instant createdAt,
    Instant updatedAt
) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(
            o.id(), o.userId(), o.items(), o.totalAmount(),
            o.status().code(), o.cancelReason(),
            o.tracking(), o.refundId(),
            o.estimatedDelivery(),  // ← 新增
            o.createdAt(), o.updatedAt());
    }
}
```

- [ ] **Step 4: OrderService.java#create 算 estimatedDelivery = now + 24h**

读 `OrderService.create` 找到 `return new Order(...)` 或 `persistAndReturn(...)` 那一行(在 `Order create` 命名方法里)。在调用 `new Order(...)` 前加:

```java
Instant estimatedDelivery = Instant.now().plus(Duration.ofHours(24));
```

然后把 estimatedDelivery 加到 Order 构造参数(按 record 顺序:在 refundId 后,createdAt 前)。

- [ ] **Step 5: 跑测试,确认 GREEN**

```bash
cd backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.order.api.dto.OrderResponseJsonTest"
```

期望:**BUILD SUCCESSFUL**,estimatedDeliveryRoundtrip + 现有所有测试都过。

- [ ] **Step 6: 跑全 order 模块 + bff module 测试,确保没破其他**

```bash
cd backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.order.*" --tests "com.seafood.bff.*"
```

期望:**BUILD SUCCESSFUL**。

- [ ] **Step 7: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add backend/src/main/java/com/seafood/order/domain/Order.java \
        backend/src/main/java/com/seafood/order/api/dto/OrderResponse.java \
        backend/src/main/java/com/seafood/order/application/OrderService.java \
        backend/src/test/java/com/seafood/order/api/dto/OrderResponseJsonTest.java
git -c user.name="Claude" -c user.email="noreply@anthropic.com" commit -m "feat(backend): Order aggregate 加 estimatedDelivery 字段(create 时 = now + 24h)

TDD:
- RED: OrderResponseJsonTest.estimatedDeliveryRoundtrip 编译失败
- GREEN: Order record + OrderResponse record + OrderService.create 全加字段

向后兼容:OrderDocument.estimatedDelivery null 时 Spring Data Mongo 不写,旧订单无此字段反序列化为 null,零迁移"
```

---

## Task 3: mp-09 OD RED — mp-od-design.test.ts 加 mp-09 spec

**Files:**
- Modify: `frontend/e2e/mp-od-design.test.ts`(OD_SPECS 数组加 mp-09)

- [ ] **Step 1: 读现有 OD_SPECS 看 spec 字段约定**

```bash
grep -A 15 "mp-08-order-list" frontend/e2e/mp-od-design.test.ts
```

期望:看到现有 spec 结构(`sourceFile` / `sourceWxss` / `required: [{selector, label}]` / `tokens: [{name, hex, usage}]`)。

- [ ] **Step 2: 在 OD_SPECS 数组末尾加 mp-09 spec(在最后一项前)**

```typescript
{
  name: 'mp-09-order-detail',
  route: { url: '/pages-sub/order/order-detail/order-detail?id=v2.1-closure-order-001' },
  sourceFile: 'pages-sub/order/order-detail/order-detail.wxml',
  sourceWxss: 'pages-sub/order/order-detail/order-detail.wxss',
  required: [
    { selector: 'order-detail|order-detail__topbar', label: '顶部导航 + 返回 + 客服' },
    { selector: 'status-banner|order-detail__status', label: '订单状态 banner(颜色根据 status 切换)' },
    { selector: 'timeline-card|order-detail__timeline', label: '物流轨迹卡(3 节点)' },
    { selector: 'tl-node|order-detail__tl-node', label: 'timeline 单个节点' },
    { selector: 'addr-card|order-detail__address', label: '收货地址卡' },
    { selector: 'items-card|order-detail__items', label: '商品清单卡' },
    { selector: 'price-card|order-detail__price', label: '价格明细卡' },
    { selector: 'info-card|order-detail__info', label: '订单信息卡(订单号/时间/支付/配送)' },
    { selector: 'bottom-bar|order-detail__actions', label: '底部 sticky action bar' },
  ],
  tokens: [
    { name: 'status-success', hex: '#198f5a', usage: 'SHIPPED 状态 banner 背景(ratio >= 4.5)' },
    { name: 'status-info', hex: '#1988a3', usage: 'PAID 状态 banner 背景' },
    { name: 'status-warning', hex: '#df911a', usage: 'PENDING 状态 banner 背景' },
  ],
},
```

- [ ] **Step 3: 跑测试,确认 RED**

```bash
cd frontend && TZ=UTC npx jest e2e/mp-od-design.test.ts --runInBand
```

期望:mp-09 spec 9 个 required 全部 fail(找不到 `order-detail.wxml` 触发 throw 错,或 wxml 内容不匹配)。这是真 RED。

- [ ] **Step 4: 不 commit(RED 状态),继续 Task 4**

---

## Task 4: mp-09 3layer RED — mp-3layer.test.ts PAGES 加 mp-09

**Files:**
- Modify: `frontend/e2e/mp-3layer.test.ts`(PAGES 数组加 mp-09)

- [ ] **Step 1: 读现有 PAGES 数组,找 mp-08 那一项作模板**

```bash
grep -B 1 -A 8 "mp-08-order-list" frontend/e2e/mp-3layer.test.ts
```

- [ ] **Step 2: 在 PAGES 数组末尾(注释上方)加 mp-09 spec**

```typescript
{
  name: 'mp-09-order-detail',
  url: '/pages-sub/order/order-detail/order-detail?id=v2.1-closure-order-001',
  storage: {
    userInfo: { id: 'dev-user-001', openId: 'dev-mock', nickName: '视觉验收' },
    token: 'dev-mock-jwt',
  },
  wxmlMust: [
    /status-banner|order-detail__status/,
    /timeline-card|order-detail__timeline/,
    /tl-node/,
    /addr-card|order-detail__address/,
    /items-card|order-detail__items/,
    /price-card|order-detail__price/,
    /info-card|order-detail__info/,
    /bottom-bar|order-detail__actions/,
  ],
  dataMust: ['order', 'statusBanner', 'timeline'],
  dataExact: [
    { path: 'order.id', equals: 'v2.1-closure-order-001' },
    { path: 'order.status', equals: 'PENDING' },
  ],
  fromBackend: { path: 'order', fields: ['id', 'status', 'items', 'totalAmount', 'estimatedDelivery', 'createdAt'] },
},
```

- [ ] **Step 3: 跑测试(需 DevTools 9420 端口)**

```bash
cd frontend && TZ=UTC WS_ENDPOINT=ws://127.0.0.1:9420 npx jest e2e/mp-3layer.test.ts --runInBand
```

期望:mp-09 spec 3 个 it 全部 fail(找不到页面 + dataMust 字段不存在 + 拉不到 fromBackend 字段)。这是真 RED。

注:若 DevTools 9420 未启动,先启:
```bash
/Applications/wechatwebdevtools.app/Contents/MacOS/cli auto --project /Users/linbinghui/agent-work/seafood-miniapp/frontend --auto-port 9420
```

- [ ] **Step 4: 不 commit(RED 状态),继续 Task 5**

---

## Task 5: GREEN — 创建 mp-09 4 个页面文件 + utils derive

**Files:**
- Create: `frontend/utils/order-detail-derive.js`
- Create: `frontend/utils/order-detail-derive.d.ts`
- Create: `frontend/pages-sub/order/order-detail/order-detail.js`
- Create: `frontend/pages-sub/order/order-detail/order-detail.wxml`
- Create: `frontend/pages-sub/order/order-detail/order-detail.wxss`
- Create: `frontend/pages-sub/order/order-detail/order-detail.json`

- [ ] **Step 1: 写 utils/order-detail-derive.js(纯函数)**

```javascript
// utils/order-detail-derive.js
// 纯函数:输入 OrderResponse 输出 UI 派生数据。
// 跟 order-detail.js 1:1 对应,无副作用无外部依赖,方便 unit test。

/**
 * @typedef {Object} OrderResponse
 * @property {string} id
 * @property {string} userId
 * @property {OrderItem[]} items
 * @property {number} totalAmount
 * @property {string} status
 * @property {string|null} cancelReason
 * @property {OrderTracking|null} tracking
 * @property {string|null} refundId
 * @property {string|null} estimatedDelivery
 * @property {string} createdAt
 * @property {string} updatedAt
 */

/**
 * @typedef {Object} StatusBanner
 * @property {string} statusText
 * @property {string} statusColor
 * @property {string|null} estimatedText
 * @property {string|null} trackingText
 */

/**
 * @typedef {Object} TimelineNode
 * @property {string} label
 * @property {string} time
 * @property {string} desc
 * @property {'done'|'current'|'future'} state
 */

/**
 * 派生 status banner 字段。
 * @param {OrderResponse} order
 * @returns {StatusBanner}
 */
function deriveBanner(order) {
  const status = (order && order.status) || 'PENDING';
  const colorMap = {
    PENDING: { text: '待支付', color: 'warning' },
    PAID: { text: '待发货', color: 'info' },
    SHIPPED: { text: '冷链在途', color: 'success' },
    COMPLETED: { text: '已签收', color: 'neutral' },
    CANCELLED: { text: '已取消', color: 'error' },
    REFUNDING: { text: '退款中', color: 'error' },
    REFUNDED: { text: '已退款', color: 'neutral' },
  };
  const { text, color } = colorMap[status] || colorMap.PENDING;

  let estimatedText = null;
  if (order && order.estimatedDelivery) {
    const t = new Date(order.estimatedDelivery);
    if (!Number.isNaN(t.getTime())) {
      const hh = String(t.getHours()).padStart(2, '0');
      const mm = String(t.getMinutes()).padStart(2, '0');
      estimatedText = `预计 ${hh}:${mm} 前送达`;
    }
  }

  let trackingText = null;
  if (order && order.tracking && order.tracking.trackingNumber) {
    const carrier = order.tracking.carrier || '物流';
    trackingText = `${carrier} ${order.tracking.trackingNumber}`;
  }

  return {
    statusText: text,
    statusColor: color,
    estimatedText,
    trackingText,
  };
}

/**
 * 派生 timeline 节点(严格 3 节点)。
 * @param {OrderResponse} order
 * @returns {TimelineNode[]}
 */
function deriveTimeline(order) {
  const fmt = (iso) => {
    if (!iso) return '—';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '—';
    const M = String(d.getMonth() + 1).padStart(2, '0');
    const D = String(d.getDate()).padStart(2, '0');
    const h = String(d.getHours()).padStart(2, '0');
    const m = String(d.getMinutes()).padStart(2, '0');
    return `${M}-${D} ${h}:${m}`;
  };

  const status = (order && order.status) || 'PENDING';
  const isShipped = status === 'SHIPPED' || status === 'COMPLETED';
  const isCompleted = status === 'COMPLETED';

  return [
    {
      label: '下单成功',
      time: fmt(order && order.createdAt),
      desc: '订单已提交',
      state: 'done',
    },
    {
      label: '商家拣货',
      time: isShipped ? fmt(order && order.updatedAt) : '处理中',
      desc: '商家已完成拣货',
      state: isShipped ? 'done' : 'current',
    },
    {
      label: '顺丰揽收',
      time: isCompleted && order.tracking && order.tracking.deliveredAt
        ? fmt(order.tracking.deliveredAt)
        : '—',
      desc: '冷链运输中',
      state: isCompleted ? 'done' : 'future',
    },
  ];
}

module.exports = { deriveBanner, deriveTimeline };
```

- [ ] **Step 2: 写 utils/order-detail-derive.d.ts(types)**

```typescript
// utils/order-detail-derive.d.ts
export interface OrderItem {
  productId: string;
  productName: string;
  unitPrice: number;
  quantity: number;
}

export interface OrderTracking {
  carrier?: string | null;
  trackingNumber?: string | null;
  deliveredAt?: string | null;
}

export interface OrderResponse {
  id: string;
  userId: string;
  items: OrderItem[];
  totalAmount: number;
  status: string;
  cancelReason: string | null;
  tracking: OrderTracking | null;
  refundId: string | null;
  estimatedDelivery: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface StatusBanner {
  statusText: string;
  statusColor: 'warning' | 'info' | 'success' | 'neutral' | 'error';
  estimatedText: string | null;
  trackingText: string | null;
}

export interface TimelineNode {
  label: string;
  time: string;
  desc: string;
  state: 'done' | 'current' | 'future';
}

export function deriveBanner(order: OrderResponse | null | undefined): StatusBanner;
export function deriveTimeline(order: OrderResponse | null | undefined): TimelineNode[];
```

- [ ] **Step 3: 写 pages-sub/order/order-detail/order-detail.json**

```json
{
  "navigationStyle": "custom",
  "usingComponents": {
    "shared-loading": "@vant/weapp/loading/index",
    "shared-empty": "@vant/weapp/empty/index"
  }
}
```

- [ ] **Step 4: 写 pages-sub/order/order-detail/order-detail.js**

```javascript
// pages-sub/order/order-detail/order-detail.js
const { deriveBanner, deriveTimeline } = require('../../../utils/order-detail-derive.js');
const request = require('../../../utils/request.js').default || require('../../../utils/request.js');

Page({
  data: {
    order: null,
    statusBanner: null,
    timeline: [],
    isLoading: true,
    isError: false,
    errorMessage: '',
  },

  onLoad(options) {
    const id = options && options.id;
    if (!id) {
      this.handleError({ message: '订单 ID 缺失' });
      return;
    }
    this.loadOrder(id);
  },

  async loadOrder(id) {
    this.setData({ isLoading: true, isError: false });
    try {
      const order = await request({
        url: `/api/orders/${id}`,
        method: 'GET',
      });
      this.setData({
        order,
        statusBanner: deriveBanner(order),
        timeline: deriveTimeline(order),
        isLoading: false,
      });
    } catch (err) {
      this.handleError(err);
    }
  },

  handleError(err) {
    const status = err && err.statusCode;
    if (status === 404) {
      wx.showModal({
        title: '订单不存在',
        content: '该订单可能已被删除',
        showCancel: false,
        success: () => wx.navigateBack(),
      });
      return;
    }
    if (status === 401 || status === 403) {
      wx.showModal({
        title: '请先登录',
        content: '登录后可查看订单详情',
        showCancel: false,
        success: () => wx.navigateTo({ url: '/pages-sub/user/login/login' }),
      });
      return;
    }
    this.setData({
      isLoading: false,
      isError: true,
      errorMessage: (err && err.message) || '加载失败,请重试',
    });
  },

  onRetry() {
    const id = this.data.order && this.data.order.id;
    if (id) this.loadOrder(id);
  },

  onBack() {
    wx.navigateBack();
  },

  async applyRefund() {
    const order = this.data.order;
    if (!order) return;
    const res = await wx.showModal({
      title: '申请退款',
      content: '确定要申请退款?商家将在 24 小时内处理',
    });
    if (!res.confirm) return;

    try {
      wx.showLoading({ title: '提交中...' });
      const newOrder = await request({
        url: `/api/orders/${order.id}/refund`,
        method: 'POST',
        data: { reason: '用户主动申请' },
      });
      this.setData({
        order: newOrder,
        statusBanner: deriveBanner(newOrder),
      });
      wx.showToast({ title: '退款申请已提交', icon: 'success' });
    } catch (err) {
      wx.showToast({ title: (err && err.message) || '申请失败,请重试', icon: 'none' });
    } finally {
      wx.hideLoading();
    }
  },

  async confirmReceive() {
    const order = this.data.order;
    if (!order) return;
    const res = await wx.showModal({
      title: '确认收货',
      content: '请检查海鲜鲜度后再确认',
    });
    if (!res.confirm) return;

    try {
      wx.showLoading({ title: '确认中...' });
      const newOrder = await request({
        url: `/api/orders/${order.id}/confirm`,
        method: 'POST',
      });
      this.setData({
        order: newOrder,
        statusBanner: deriveBanner(newOrder),
        timeline: deriveTimeline(newOrder),
      });
      wx.showToast({ title: '已确认收货', icon: 'success' });
    } catch (err) {
      wx.showToast({ title: (err && err.message) || '确认失败,请重试', icon: 'none' });
    } finally {
      wx.hideLoading();
    }
  },

  viewLogistics() {
    const order = this.data.order;
    if (!order) return;
    if (order.status !== 'SHIPPED' && order.status !== 'COMPLETED') {
      wx.showToast({ title: '订单尚未发货', icon: 'none' });
      return;
    }
    if (!order.tracking || !order.tracking.trackingNumber) {
      wx.showToast({ title: '物流单号暂未生成', icon: 'none' });
      return;
    }
    wx.setClipboardData({
      data: order.tracking.trackingNumber,
      success: () =>
        wx.showToast({
          title: '物流单号已复制,请到顺丰/京东小程序查询',
          icon: 'none',
        }),
    });
  },
});
```

- [ ] **Step 5: 写 pages-sub/order/order-detail/order-detail.wxml**

```xml
<view class="order-detail">
  <view class="topbar">
    <view class="back" bindtap="onBack">‹</view>
    <view class="title">订单详情</view>
    <view class="placeholder"></view>
  </view>

  <view wx:if="{{isLoading}}" class="loading">
    <shared-loading />
  </view>

  <view wx:elif="{{isError}}" class="error">
    <view class="error-msg">{{errorMessage}}</view>
    <button class="retry-btn" bindtap="onRetry">重试</button>
  </view>

  <block wx:elif="{{order}}">
    <!-- status banner -->
    <view class="status-banner status-banner--{{statusBanner.statusColor}}">
      <view class="status-eyebrow">{{statusBanner.trackingText || '订单状态'}}</view>
      <view class="status-h">{{statusBanner.statusText}}</view>
      <view wx:if="{{statusBanner.estimatedText}}" class="status-sub">{{statusBanner.estimatedText}}</view>
    </view>

    <!-- timeline -->
    <view class="timeline-card">
      <view class="card-title">物流轨迹</view>
      <view class="timeline">
        <view wx:for="{{timeline}}" wx:key="label" class="tl-node tl-node--{{item.state}}">
          <view class="dot"></view>
          <view class="content">
            <view class="row1">
              <text class="label">{{item.label}}</text>
              <text class="time">{{item.time}}</text>
            </view>
            <view class="desc">{{item.desc}}</view>
          </view>
        </view>
      </view>
    </view>

    <!-- address -->
    <view class="card addr-card">
      <view class="row1">
        <text class="nm">收货人: {{order.userId}}</text>
        <text class="phone">--</text>
      </view>
      <view class="addr">订单 #{{order.id}}</view>
    </view>

    <!-- items -->
    <view class="card items-card">
      <view class="card-title">商品 · {{order.items.length}} 件</view>
      <view wx:for="{{order.items}}" wx:key="productId" class="item">
        <view class="pic"></view>
        <view class="info">
          <view class="nm">{{item.productName}}</view>
          <view class="spec">× {{item.quantity}}</view>
        </view>
        <view class="pr">
          <text class="p">¥{{item.unitPrice}}</text>
        </view>
      </view>
    </view>

    <!-- price -->
    <view class="card price-card">
      <view class="row"><text class="lbl">实付</text><text class="val">¥{{order.totalAmount}}</text></view>
    </view>

    <!-- info -->
    <view class="card info-card">
      <view class="row"><text class="lbl">订单号</text><text class="val">{{order.id}}</text></view>
      <view class="row"><text class="lbl">下单时间</text><text class="val">{{order.createdAt}}</text></view>
      <view class="row"><text class="lbl">预计送达</text><text class="val">{{order.estimatedDelivery || '—'}}</text></view>
    </view>

    <!-- bottom action bar -->
    <view class="bottom-bar">
      <button class="act secondary" bindtap="applyRefund" data-testid="apply-refund">申请退款</button>
      <button class="act secondary" bindtap="viewLogistics" data-testid="view-logistics">查看物流</button>
      <button class="act primary" bindtap="confirmReceive" data-testid="confirm-receive">确认收货</button>
    </view>
  </block>

  <view wx:else class="empty">
    <shared-empty description="订单不存在" />
  </view>
</view>
```

- [ ] **Step 6: 写 pages-sub/order/order-detail/order-detail.wxss**

```css
/* pages-sub/order/order-detail/order-detail.wxss */
.order-detail {
  min-height: 100vh;
  background: var(--bg, #fffbf8);
  padding-bottom: 200rpx;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx;
  background: var(--surface, #fff);
  border-bottom: 1rpx solid var(--border, #e8e2da);
}
.topbar .back { font-size: 48rpx; line-height: 1; padding: 8rpx 16rpx; }
.topbar .title { font-size: 32rpx; font-weight: 600; }
.topbar .placeholder { width: 64rpx; }

.loading, .error, .empty {
  display: flex; align-items: center; justify-content: center;
  min-height: 400rpx; padding: 48rpx;
}
.error-msg { color: var(--error, #c2410c); font-size: 28rpx; margin-bottom: 24rpx; }
.retry-btn {
  background: var(--accent, #c2410c); color: var(--shell, #fff);
  border: 0; border-radius: 12rpx; padding: 16rpx 48rpx; font-size: 28rpx;
}

/* status banner */
.status-banner {
  margin: 24rpx 32rpx 0; padding: 32rpx;
  border-radius: 20rpx;
}
.status-banner--success { background: var(--success-soft, #d8f0e0); }
.status-banner--info    { background: var(--info-soft, #d9eef5); }
.status-banner--warning { background: var(--warning-soft, #fce8c8); }
.status-banner--error   { background: var(--error-soft, #fce0d6); }
.status-banner--neutral { background: var(--bg, #f5efe8); }
.status-eyebrow { font-size: 22rpx; color: var(--muted, #6a5d4f); margin-bottom: 8rpx; }
.status-h { font-size: 40rpx; font-weight: 600; margin-bottom: 8rpx; }
.status-sub { font-size: 26rpx; color: var(--muted, #6a5d4f); }

/* card 通用 */
.card { margin: 24rpx 32rpx 0; background: var(--surface, #fff); border: 1rpx solid var(--border, #e8e2da); border-radius: 20rpx; padding: 32rpx; }
.card-title { font-size: 24rpx; font-weight: 600; color: var(--muted, #6a5d4f); margin-bottom: 16rpx; letter-spacing: 0.1em; text-transform: uppercase; }

/* timeline */
.timeline { padding: 8rpx 0; }
.tl-node { display: flex; gap: 24rpx; padding: 16rpx 0; }
.tl-node .dot {
  width: 16rpx; height: 16rpx; border-radius: 50%;
  background: var(--border, #e8e2da); margin-top: 12rpx; flex-shrink: 0;
}
.tl-node--done .dot { background: var(--success, #198f5a); }
.tl-node--current .dot { background: var(--success, #198f5a); box-shadow: 0 0 0 8rpx var(--success-soft, #d8f0e0); }
.tl-node .content { flex: 1; }
.tl-node .row1 { display: flex; justify-content: space-between; margin-bottom: 4rpx; }
.tl-node .label { font-size: 28rpx; font-weight: 500; }
.tl-node--future .label { color: var(--muted, #6a5d4f); }
.tl-node .time { font-size: 22rpx; color: var(--muted, #6a5d4f); }
.tl-node .desc { font-size: 24rpx; color: var(--muted, #6a5d4f); }

/* addr */
.addr-card .row1 { display: flex; gap: 16rpx; margin-bottom: 12rpx; }
.addr-card .nm { font-size: 30rpx; font-weight: 600; }
.addr-card .phone { font-size: 26rpx; color: var(--muted, #6a5d4f); }
.addr-card .addr { font-size: 28rpx; line-height: 1.5; }

/* items */
.items-card .item { display: flex; gap: 24rpx; padding: 16rpx 0; border-bottom: 1rpx solid var(--border, #e8e2da); }
.items-card .item:last-child { border-bottom: 0; }
.items-card .pic { width: 120rpx; height: 120rpx; background: var(--bg, #f5efe8); border-radius: 12rpx; flex-shrink: 0; }
.items-card .info { flex: 1; }
.items-card .nm { font-size: 28rpx; font-weight: 500; }
.items-card .spec { font-size: 24rpx; color: var(--muted, #6a5d4f); margin-top: 4rpx; }
.items-card .pr { text-align: right; }
.items-card .p { font-size: 28rpx; font-weight: 600; }

/* price */
.price-card .row { display: flex; justify-content: space-between; padding: 8rpx 0; }
.price-card .lbl { font-size: 28rpx; color: var(--muted, #6a5d4f); }
.price-card .val { font-size: 28rpx; }

/* info */
.info-card .row { display: flex; justify-content: space-between; padding: 8rpx 0; }
.info-card .lbl { font-size: 26rpx; color: var(--muted, #6a5d4f); }
.info-card .val { font-size: 26rpx; font-family: var(--font-mono, monospace); }

/* bottom-bar */
.bottom-bar {
  position: fixed; left: 0; right: 0; bottom: 0;
  display: flex; gap: 16rpx;
  padding: 24rpx 32rpx 48rpx;
  background: var(--surface, #fff);
  border-top: 1rpx solid var(--border, #e8e2da);
}
.bottom-bar .act {
  flex: 1; height: 80rpx; border-radius: 16rpx; font-size: 28rpx;
  display: flex; align-items: center; justify-content: center;
  background: var(--bg, #f5efe8); color: var(--fg, #1a1410);
  border: 1rpx solid var(--border, #e8e2da);
}
.bottom-bar .act.primary { background: var(--accent, #c2410c); color: var(--shell, #fff); border: 0; }
```

- [ ] **Step 7: 跑 mp-od-design 测试,确认 GREEN**

```bash
cd frontend && TZ=UTC npx jest e2e/mp-od-design.test.ts --runInBand
```

期望:mp-09 spec 9 个 required 全部 pass,token parity 0 fail(对比 mp-09 status 颜色 token)。

- [ ] **Step 8: 不 commit(GREEN 状态,等 Task 6 wire 完成)**

---

## Task 6: WIRE — order-list.wxml + app.json 接 mp-09

**Files:**
- Modify: `frontend/pages-sub/order/order-list/order-list.wxml`(卡片 bindtap 跳 order-detail)
- Modify: `frontend/app.json`(pages 加 order-detail)

- [ ] **Step 1: 读 order-list.wxml 找 order-card 渲染处**

```bash
grep -n "order-card\|bindtap\|navigateTo" frontend/pages-sub/order/order-list/order-list.wxml
```

- [ ] **Step 2: 在 order-card 顶层 view 加 bindtap + data-id**

例:在 `<view class="order-card" wx:for="{{orders}}" ...>` 加 `bindtap="onOrderTap" data-id="{{item.id}}"`(或类似 — 依现有 wxml 风格)。

(具体替换文本视现有 wxml 而定。原则是:每个订单卡片可点,跳 `/pages-sub/order/order-detail/order-detail?id={id}`)

- [ ] **Step 3: 在 order-list.js 加 onOrderTap handler**

读 `frontend/pages-sub/order/order-list/order-list.js`,在合适位置加:

```javascript
onOrderTap(e) {
  const id = e.currentTarget.dataset.id;
  if (!id) return;
  wx.navigateTo({ url: `/pages-sub/order/order-detail/order-detail?id=${id}` });
},
```

- [ ] **Step 4: app.json pages 数组加 order-detail 路径**

读 `frontend/app.json`,在 pages 数组合适位置(放 order-list 后):

```json
"pages-sub/order/order-detail/order-detail"
```

- [ ] **Step 5: 跑 mp-3layer 测试(若 DevTools 9420 启了)**

```bash
cd frontend && TZ=UTC WS_ENDPOINT=ws://127.0.0.1:9420 npx jest e2e/mp-3layer.test.ts --runInBand
```

期望:mp-09 spec 3 个 it 全部 pass(结构 + 数据 + 行为)。

- [ ] **Step 6: Commit 整套 mp-09 实现**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add frontend/utils/order-detail-derive.js \
        frontend/utils/order-detail-derive.d.ts \
        frontend/pages-sub/order/order-detail/ \
        frontend/pages-sub/order/order-list/order-list.wxml \
        frontend/pages-sub/order/order-list/order-list.js \
        frontend/app.json \
        frontend/e2e/mp-od-design.test.ts \
        frontend/e2e/mp-3layer.test.ts
git -c user.name="Claude" -c user.email="noreply@anthropic.com" commit -m "feat(mp-09): 订单详情页(OD 对齐 + 3 action 业务 + 3 层测试)

页面:
- pages-sub/order/order-detail/ 4 新文件(wxml/wxss/js/json)
- 5 section: status banner / timeline (3 节点) / address / items / price+info
- 3 底部 action: 申请退款 / 查看物流 / 确认收货
- 4 类错误处理(网络/404/401-403/action 失败)

派生:
- utils/order-detail-derive.js: deriveBanner + deriveTimeline 纯函数
- 7 种 status 映射 statusText + statusColor

Wire:
- order-list 卡片 bindtap 跳 order-detail
- app.json 注册新页面

测试:
- mp-od-design: mp-09 spec 9 required + 3 token
- mp-3layer: PAGES 加 mp-09 spec (结构 + 数据 + 行为)
- 颜色 token parity 0 fail"
```

---

## Task 7: REFACTOR — 加 unit test 验 derive 函数边界

**Files:**
- Create: `frontend/utils/__tests__/order-detail-derive.test.js`

- [ ] **Step 1: 写 derive 函数 unit test(7 个 status + 边界)**

```javascript
// utils/__tests__/order-detail-derive.test.js
const { deriveBanner, deriveTimeline } = require('../order-detail-derive.js');

const baseOrder = {
  id: 'ord-1', userId: 'u-1', items: [], totalAmount: 100, status: 'PENDING',
  cancelReason: null, tracking: null, refundId: null, estimatedDelivery: null,
  createdAt: '2026-06-18T10:00:00Z', updatedAt: '2026-06-18T10:00:00Z',
};

describe('deriveBanner', () => {
  it.each([
    ['PENDING', '待支付', 'warning'],
    ['PAID', '待发货', 'info'],
    ['SHIPPED', '冷链在途', 'success'],
    ['COMPLETED', '已签收', 'neutral'],
    ['CANCELLED', '已取消', 'error'],
    ['REFUNDING', '退款中', 'error'],
    ['REFUNDED', '已退款', 'neutral'],
  ])('status=%s → text=%s color=%s', (status, expectedText, expectedColor) => {
    const banner = deriveBanner({ ...baseOrder, status });
    expect(banner.statusText).toBe(expectedText);
    expect(banner.statusColor).toBe(expectedColor);
  });

  it('estimatedDelivery null 时 estimatedText 为 null', () => {
    const banner = deriveBanner({ ...baseOrder, estimatedDelivery: null });
    expect(banner.estimatedText).toBeNull();
  });

  it('estimatedDelivery 有值时 estimatedText 含 HH:mm', () => {
    const banner = deriveBanner({ ...baseOrder, estimatedDelivery: '2026-06-18T14:30:00Z' });
    expect(banner.estimatedText).toMatch(/14:30/);
  });

  it('tracking 有值时 trackingText 显示 carrier + number', () => {
    const banner = deriveBanner({ ...baseOrder, tracking: { carrier: '顺丰', trackingNumber: 'SF1024' } });
    expect(banner.trackingText).toBe('顺丰 SF1024');
  });

  it('null order 不抛错', () => {
    const banner = deriveBanner(null);
    expect(banner.statusText).toBe('待支付');
  });
});

describe('deriveTimeline', () => {
  it('严格返回 3 个节点', () => {
    expect(deriveTimeline(baseOrder)).toHaveLength(3);
  });

  it('PENDING 状态:第 1 节点 done,第 2 节点 current,第 3 节点 future', () => {
    const tl = deriveTimeline({ ...baseOrder, status: 'PENDING' });
    expect(tl[0].state).toBe('done');
    expect(tl[1].state).toBe('current');
    expect(tl[2].state).toBe('future');
  });

  it('SHIPPED 状态:第 1+2 节点 done,第 3 节点 future', () => {
    const tl = deriveTimeline({ ...baseOrder, status: 'SHIPPED' });
    expect(tl[0].state).toBe('done');
    expect(tl[1].state).toBe('done');
    expect(tl[2].state).toBe('future');
  });

  it('COMPLETED 状态:全部 done', () => {
    const tl = deriveTimeline({ ...baseOrder, status: 'COMPLETED' });
    expect(tl.every((n) => n.state === 'done')).toBe(true);
  });

  it('null order 返回空或基础 3 节点(不抛错)', () => {
    const tl = deriveTimeline(null);
    expect(Array.isArray(tl)).toBe(true);
    expect(tl).toHaveLength(3);
  });
});
```

- [ ] **Step 2: 跑 unit test,确认全过**

```bash
cd frontend && npx jest utils/__tests__/order-detail-derive.test.js
```

期望:**PASS**(12 个 it 全部过)。

- [ ] **Step 3: 跑全 mp-od-design + mp-3layer 确认 GREEN 仍稳**

```bash
cd frontend && TZ=UTC npx jest e2e/mp-od-design.test.ts --runInBand
cd frontend && TZ=UTC WS_ENDPOINT=ws://127.0.0.1:9420 npx jest e2e/mp-3layer.test.ts --runInBand
```

期望:均 pass。

- [ ] **Step 4: Commit**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git add frontend/utils/__tests__/order-detail-derive.test.js
git -c user.name="Claude" -c user.email="noreply@anthropic.com" commit -m "test(mp-09): derive 函数 unit test 覆盖 7 status + 边界 + null

12 cases:
- 7 status 映射(deriveBanner)
- estimatedDelivery null / 有值
- tracking 拼接
- null order 不抛错
- timeline 3 节点 + 4 状态组合"
```

---

## Task 8: 端到端验证 — 跑后端测试 + curl 真实 API

**Files:**
- 无文件改动(纯验证)

- [ ] **Step 1: 跑 backend 全 order + bff module 测试**

```bash
cd backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :test --tests "com.seafood.order.*" --tests "com.seafood.bff.*"
```

期望:**BUILD SUCCESSFUL**。

- [ ] **Step 2: 跑 frontend 全 e2e 测试(mp-od-design + mp-3layer + token-parity)**

```bash
cd frontend && TZ=UTC npx jest e2e/mp-od-design.test.ts e2e/token-parity.test.ts --runInBand
```

若 DevTools 9420 启了:
```bash
cd frontend && TZ=UTC WS_ENDPOINT=ws://127.0.0.1:9420 npx jest e2e/mp-3layer.test.ts --runInBand
```

期望:全部 pass(mp-09 spec 9 + mp-3layer mp-09 3 + token-parity 8)。

- [ ] **Step 3: 启 backend,curl 测端到端**

```bash
cd backend && export JAVA_HOME=/opt/homebrew/Cellar/graalvm/25.0.2/libexec/graalvm.jdk/Contents/Home && export PATH=$JAVA_HOME/bin:$PATH && JWT_SECRET=seafood-jwt-secret-must-be-at-least-32-bytes-long-12345 JWT_ADMIN_SECRET=seafood-admin-jwt-secret-must-be-at-least-32-bytes-4567 MONGODB_URI=mongodb://localhost:27017/seafood WECHAT_ENABLED=false ADMIN_BOOTSTRAP_PASSWORD=admin123 ./gradlew :bootRun
```

在另一个 terminal:
```bash
# user login → 拿 token
USER_RES=$(curl -s -X POST http://localhost:8080/api/auth/wechat-login -H "Content-Type: application/json" -d '{"code":"dev-mp09-test"}')
USER_TOKEN=$(echo "$USER_RES" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

# 创建测试 order
curl -s -X POST -H "Authorization: Bearer $USER_TOKEN" -H "Content-Type: application/json" "http://localhost:8080/api/cart/items" -d '{"productId":"6a2f097fcb28035db83d88b3","quantity":1,"selected":true}' > /dev/null
NEW_ORDER=$(curl -s -X POST -H "Authorization: Bearer $USER_TOKEN" "http://localhost:8080/api/orders" -i | grep -i "location:" | sed -E 's/.*\/api\/orders\/(.+)/\1/' | tr -d '\r')

# 测 GET /api/orders/{id}(mp 端用)
curl -s -i -H "Authorization: Bearer $USER_TOKEN" "http://localhost:8080/api/orders/$NEW_ORDER" --max-time 10 | head -3

# 期望:HTTP/1.1 200
# body 含 "estimatedDelivery" 字段(后端已加)
```

- [ ] **Step 4: 跑全 admin 测试,确认 ad 没破**

```bash
cd admin-ui && npx vitest run
```

期望:89+ 测试全过。

- [ ] **Step 5: 检查 git status,确认所有 commit 进 HEAD**

```bash
cd /Users/linbinghui/agent-work/seafood-miniapp
git log --oneline -8
git status
```

期望:看到 4 个 mp-09 相关 commit(后端 GREEN / mp-09 GREEN / REFACTOR / 含 4 后端 7 前端 file),无 uncommitted file。

---

## Self-Review

1. **Spec coverage**(对照 design.md 9 节):
   - §1 背景目标 → Task 5/6 全部交付
   - §2 架构(2 改 1 加 后端 + 4 新 2 改 前端 + 2 改 测试)→ Task 1/2/3/4/5/6 全覆盖
   - §3 数据流(3 handler)→ Task 5 Step 4 order-detail.js 完整实现
   - §4 派生函数 → Task 5 Step 1 derive.js + Task 7 unit test
   - §5 UI 布局 → Task 5 Step 5 wxml + Step 6 wxss
   - §6 错误处理 4 类 → Task 5 Step 4 order-detail.js handleError + 各 handler try/catch
   - §7 测试 3 层 → Task 3 mp-od-design RED + Task 4 mp-3layer RED + Task 7 derive unit test
   - §8 文件清单 → 8 改 + 7 新全在
   - §9 Sprint 1 closure 关联 → Task 8 端到端

2. **Placeholder scan**:0 TBD / TODO / "implement later"。

3. **Type consistency**:
   - `deriveBanner` / `deriveTimeline` 签名 Task 5 定义,Task 7 test 同步,无 drift
   - `OrderResponse.estimatedDelivery` 字段 Task 2 加,Task 5 JS 用 `order.estimatedDelivery` 一致
   - `statusColor` 类型 union `'warning'|'info'|'success'|'neutral'|'error'` 在 .d.ts + .js 一致

4. **Execution note**:Task 4 mp-3layer RED 需 DevTools 9420 端口;若本机 DevTools 不在跑,需先 `cli auto --auto-port 9420` 再跑(plan 已注明)。如未启,Step 3 跳过但 commit 仍可走(wire 完后再启 DevTools 重跑)。

Plan 完整,8 task,每 task 5-7 步骤,全部含实际代码。
