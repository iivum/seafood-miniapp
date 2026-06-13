# 01 · mp-01~08 用户端 8 屏功能拆解

> 范围:Open Design 项目的 `mp-01-home.html` ~ `mp-08-order-list.html` 8 屏。
> 每屏按"功能点 / 状态机 / API 依赖 / 关键交互"四列拆。
> 与现有 frontend 页面(主包 4 屏 + 6 分包屏)的对照见 `04-gap-analysis.md`。

---

## 屏映射表

| OD 屏 | 文件 | 对应现有页面 | 在 app.json 中 |
|---|---|---|---|
| mp-01 | `mp-01-home.html` | `pages/index/index` | 主包 |
| mp-02 | `mp-02-category.html` | `pages/category/category` | 主包 |
| mp-03 | `mp-03-product-detail.html` | `pages-sub/product/product-detail/product-detail` | 分包 product |
| mp-04 | `mp-04-cart.html` | `pages/cart/cart` | 主包 |
| mp-05 | `mp-05-profile.html` | `pages/profile/profile` | 主包 |
| mp-06 | `mp-06-order-confirm.html` | `pages-sub/order/order-confirm/order-confirm` | 分包 order |
| mp-07 | `mp-07-address.html` | `pages-sub/user/address/address-list` + `address/address-edit` | 分包 user |
| mp-08 | `mp-08-order-list.html` | `pages-sub/order/order-list/order-list` | 分包 order |

---

## mp-01 首页(对应 `pages/index/index`)

### 功能点

| # | 元素 | OD 实现细节 | 关键状态 |
|---|---|---|---|
| F1 | 城市定位 | 顶部 left:"厦门·高崎码头 ▾",点击切换城市弹 toast | 静态(后续可接定位 API) |
| F2 | 搜索框 | 圆角 14px,内含 SVG + placeholder + "搜索" pill;点击弹"开发中" | 仅 UI |
| F3 | Hero banner | 16:9 圆角 22px,左上"LIVE · 今日到港"badge + 右下起价 ¥68 起 | 1 张固定图 |
| F4 | 5 分类入口 | 圆形图片 icon + 中文 label;点击跳 mp-02 并带预选分类 | 跳 mp-02 |
| F5 | "今日 9 款推荐" + 4 chips 筛选 | chips:active 用墨色反白;切换 chips 弹 toast | 客户端筛选 |
| F6 | 6 商品卡 2 列瀑布 | 每张:图(tag + product photo)+ 名称(2 行截断)+ 价格 + 单位 + 加号按钮 | 跳 mp-03 |
| F7 | 4 tab 底部 | 首页 / 分类 / 购物车 / 我的 | 当前 active 用 fg + 圆点 |

### 状态机

- 进入 → 拉 6 商品 → 渲染瀑布
- 筛选切换 → 客户端过滤(OD 演示态)→ toast 提示
- 切 tab → 单 tab 切换,无跨页状态

### API 依赖

- `GET /api/products?limit=9` — 拉首页推荐(无 category 过滤)
- `GET /api/cart/count` — 购物车 tab badge 数字(OD 未实现 badge,但 tab 已有圆点)

### 关键交互

- 点击 → `is-clicked` 闪一下(OD 自带交互修复)
- 加号 → toast "已加入购物车 · {商品名称}",**不弹窗不跳页**

---

## mp-02 分类(对应 `pages/category/category`)

### 功能点

| # | 元素 | 关键状态 |
|---|---|---|
| F1 | 顶部搜索 + 标题 | 静态 |
| F2 | 左侧分类栏(5 分类) | active 项 fg 反白 + accent 边 |
| F3 | 右侧 2 列商品瀑布 | 跟随左列切换 |
| F4 | chips 筛选 | 沿用 mp-01 风格 |

### 状态机

- 进入 → 默认选第 1 分类 → 拉该分类下商品
- 切分类 → 重置 chips → 重新拉数据
- 加购 → 同 mp-01

### API 依赖

- `GET /api/products?category={id}` — 当前分类下商品

---

## mp-03 商品详情(对应 `pages-sub/product/product-detail`)

### 功能点

| # | 元素 | 关键状态 |
|---|---|---|
| F1 | 商品大图(顶部 35% 高度) | 1:1,圆角 0 0 14px 14px |
| F2 | 商品名 / 价格 / 单位 | 价格用 display serif |
| F3 | 数量 stepper(± 按钮 + 数字) | min=1, max=stock |
| F4 | 三按钮:加购 / 立即购买 / 收藏(?) | 底部 sticky |
| F5 | 详情描述(滚动) | 文本+图文混排 |
| F6 | 规格(若有 SKU) | 客户端选择 |

### 状态机

- 进入 → 拉详情 → 渲染
- 数量超 stock → 减号 disabled
- 加购 → toast,不跳
- 立即购买 → 直接跳 mp-06 订单确认,带当前 productId + qty
- 库存 0 → 加购 + 立即购买 都 disabled

### API 依赖

- `GET /api/products/{id}`
- `POST /api/cart/items`(加购)

---

## mp-04 购物车(对应 `pages/cart/cart`)

### 功能点

| # | 元素 | 关键状态 |
|---|---|---|
| F1 | 顶部标题"购物车 · N 件" | N 来自 store |
| F2 | 全选 checkbox + "已选 K 件" | 跨行同步 |
| F3 | 3 行购物车项(图 + 名 + 单价 + qty + 删除) | 单项可改 qty |
| F4 | 底部 sticky 结算栏:合计 + 结算按钮(K 件) | K=0 时按钮 disabled |
| F5 | 空车状态 | 跳 mp-01 引导 |

### 状态机

- 进入 → 拉 `GET /api/cart` → 渲染
- 勾选切换 → 客户端合计重算
- qty 改 → 调 `PATCH /api/cart/items/{id}`(debounced 300ms)
- 删 → 调 `DELETE /api/cart/items/{id}`
- 结算 → K=0 阻断;K>0 跳 mp-06

### API 依赖

- `GET /api/cart`
- `PATCH /api/cart/items/{id}`(qty 改)
- `DELETE /api/cart/items/{id}`(删)

---

## mp-05 我的(对应 `pages/profile/profile`)

### 功能点

| # | 元素 | 关键状态 |
|---|---|---|
| F1 | 顶部用户卡(头像 + 昵称 + 微信号) | 未登录态:引导登录 |
| F2 | 4 订单状态卡(待付款/待发货/待收货/已完成) | 点击跳 mp-08 带预设 status |
| F3 | 工具列表:地址管理 / 收藏 / 联系客服 / 设置 | 点击各自跳 |
| F4 | 退出登录 | 弹确认 → 清 token → 回首页 |

### 状态机

- 未登录 → 显示"点击登录"卡
- 已登录 → 拉 `GET /api/users/me` → 渲染
- 点订单卡 → 跳 mp-08 + `?status={tab}`
- 退出 → 弹窗 → 确认后调 `POST /api/auth/logout` → 清 store

### API 依赖

- `GET /api/users/me`
- `POST /api/auth/logout`

---

## mp-06 订单确认(对应 `pages-sub/order/order-confirm`)

### 功能点

| # | 元素 | 关键状态 |
|---|---|---|
| F1 | 收货地址卡(默认地址,有"更换"按钮) | 跳 mp-07 选择模式 |
| F2 | 商品清单(图 + 名 + 单价 × qty) | 来自 cart 或 mp-03 直购 |
| F3 | 配送方式:冰鲜快递(默认) / 自提 | 切换 → 改运费 |
| F4 | 备注(textarea) | max 50 字 |
| F5 | 金额明细:商品小计 + 运费 - 优惠 = 实付 | 实时算 |
| F6 | 提交订单按钮(底部 sticky) | 校验通过才可点 |

### 状态机

- 进入 → 来源判定:cart checkout / direct buy
- 切地址 → 改 store
- 改备注 → 客户端
- 提交 → 校验 → 调 `POST /api/orders` → 成功跳 mp-08 详情 / 失败 toast
- 库存不足 → 阻断 + toast 标红

### API 依赖

- `GET /api/users/me/addresses?default=true`
- `POST /api/orders`

---

## mp-07 地址管理(对应 `pages-sub/user/address/address-list` + `address-edit`)

### 功能点

| # | 元素 | 关键状态 |
|---|---|---|
| F1 | 地址卡(姓名 + 电话 + 省市区 + 详细地址 + 默认标记) | 列表模式 |
| F2 | 单选(从 mp-06 跳来)→ 选完回退带值 | 选择模式 |
| F3 | 设为默认 | 单选卡片 |
| F4 | 编辑 → 跳 address-edit | 携带 id |
| F5 | 删除 → 弹确认 | 默认地址不可删 |
| F6 | 新增按钮(底部) | 跳 address-edit(无 id) |

### address-edit 子屏

- 收件人 / 手机号(11 位正则) / 省市区 picker / 详细地址 / 设为默认 toggle
- 保存 → 调 `POST /api/users/me/addresses` 或 `PUT /api/users/me/addresses/{id}` → 弹 toast → 回退
- 校验失败 → 字段红框 + 红字

### 状态机

- 列表态:加载中 / 有数据 / 空(显示空态卡)
- 编辑态:加载原值 / 改中 / 保存中 / 错误

### API 依赖

- `GET /api/users/me/addresses`
- `POST /api/users/me/addresses`
- `PUT /api/users/me/addresses/{id}`
- `DELETE /api/users/me/addresses/{id}`

---

## mp-08 订单列表(对应 `pages-sub/order/order-list`)

### 功能点

| # | 元素 | 关键状态 |
|---|---|---|
| F1 | 顶部:返回 + 标题"我的订单" | — |
| F2 | 状态过滤 tabs(全部 / 待付款 / 待发货 / 待收货 / 待评价 / 售后) | 6 tab,active fg 反白,带数量 badge |
| F3 | 订单卡(图 + 商家名 + N 件商品 + 实付 + 状态色标) | 多行(若 N>1) |
| F4 | 卡底操作按钮(根据状态变化) | 见下方"状态 → 操作"映射 |
| F5 | 下拉刷新 / 上拉加载 | page=0 起,size=10 |
| F6 | 空态卡 | 按 tab 分别引导 |

### 状态 → 操作按钮映射

| OrderStatus | 可用操作 |
|---|---|
| PENDING(待付款) | 取消订单 / 立即付款 |
| PAID(待发货) | 提醒发货 / 申请退款 |
| SHIPPED(待收货) | 查看物流 / 确认收货 |
| COMPLETED(待评价) | 评价 / 再次购买 / 申请售后 |
| CANCELLED | 删除 / 再次购买 |

### 状态机

- 进入 → 取 `?status=` query → 默认全部
- 切 tab → 重置 page=0,重新拉
- 取消订单 → 弹确认 → 调 `POST /api/orders/{id}/cancel` → toast + 刷新
- 付款 → 调 `POST /api/orders/{id}/pay`(MVP 内为 mock,Sprint 3 接真实支付)
- 提醒发货 / 确认收货 → 调 `POST /api/orders/{id}/remind-ship` / `confirm-receive`
- 再次购买 → 把订单 items 加进 cart → 跳 mp-04
- 申请售后 / 查看物流 / 评价 → 跳后续屏(MVP 范围外,先占位 toast "开发中")

### API 依赖

- `GET /api/orders?status={s}&page={p}&size=10`
- `POST /api/orders/{id}/cancel`
- `POST /api/orders/{id}/pay`
- `POST /api/orders/{id}/remind-ship`
- `POST /api/orders/{id}/confirm-receive`
- `POST /api/orders/{id}/rebuy`(派生:返回 cart items)
- `GET /api/orders/{id}/tracking`(后端待扩展 — 见 `05-moscow-roadmap.md` § Could ①)
- `POST /api/orders/{id}/refund`(后端待扩展 — 见 § Could ②)

---

## 屏间跳转关系

```
[mp-01 首页] ─┬─→ [mp-02 分类] ────→ [mp-03 详情] ─┬─→ [mp-04 购物车]
              ├─→ [mp-03 详情] ─────────────────────┤
              ├─→ [mp-04 购物车] ────→ [mp-06 订单确认] ──→ [mp-08 订单列表]
              │                                      ↓
              │                                  [mp-07 地址](选择模式)
              └─→ [mp-05 我的] ─┬─→ [mp-08 订单列表] (带 status)
                               ├─→ [mp-07 地址](列表模式)
                               └─→ [登录](未登录态)
```

## 跨屏统一交互系统(OD 自带,见每屏 `<script>`)

- **`[data-tabs]`** — 通用 tab 切换 → 弹 toast 提示
- **`[data-toast]`** — 任何点击弹"功能开发中"或具体提示
- **`.qty` stepper** — 通用数量加减,带 min/max 边界
- **`form` submit** — 校验 → loading 800ms → toast(成功/失败)
- **`[data-action]`** — 操作按钮统一走 ACTION_MSG 表 + 弹 toast
- **死交互修复 `flashClicked()`** — 任何 `[data-action]` `[data-pill]` `[data-toggle]` 点击后,元素
  闪一下 `is-clicked` 0.18s → fade 0.6s
