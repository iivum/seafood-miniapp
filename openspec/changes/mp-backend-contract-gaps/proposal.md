## Why

`mp-od-prototype-alignment`(已归档,2026-07-04)逐屏排查 9 个小程序页面时,发现 4 个已进入「遗留问题清单」但当时判定超出该 change 范围的实现缺口。其中 2 项(购物车数量/勾选持久化 404、登录态冷启动静默失效)是生产环境真实会触发的功能性 bug,另外 2 项是实现落后于已批准 spec(`address-management` 已要求 `district` 字段、`mini-program` 已要求「立即购买」绕过购物车)。逐项独立、彼此无依赖,集中在这一个 change 里一次性补齐,避免继续挂在遗留清单里被遗忘。

## What Changes

- 新增 `PUT /api/cart/items/{productId}`(更新数量)+ `PATCH /api/cart/items/{productId}`(切换勾选态)两个购物车端点 —— `Cart`/`CartItem` 聚合已有 `selected` 字段和 `requireNonEmptySelected()`,只缺这两个操作方法 + Controller 路由;修复前端 `CartAPI.updateItem()`/`toggleItem()` 打 404、勾选态刷新/回收后丢失回退全选的问题
- `POST /api/orders` 新增可选 `items` 请求体,显式传入时绕开购物车直接用传入行建单(不读取、不清空购物车);未传时行为不变(仍从购物车建单)—— 补齐 `mini-program` spec 已要求的「立即购买 MUST NOT touch the cart store」语义,替换 mp-03 当前「先 addItem 再建单」的前端近似方案
- `Address` 领域记录补 `district` 字段(现只有 `province/city/detail`)—— 补齐已批准的 `address-management` spec(`district` 早已在 spec 里,只是实现没跟上);同步修正前端 `detailAddress` 引用为 spec 定义的 `detail` 字段名
- `app.js` `onLaunch` 时从 `tokenStorage`(新版 `accessToken`)桥接初始化 `globalData.token`,修复冷启动「已登录但本次未触发 login()」场景下仍在用 legacy `utils/request.js` 发鉴权请求的代码静默不带 token 的问题(已知影响 mp-04 购物车 + mp-06 订单确认页默认地址自动选中)

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `backend-api`: 「Customer cart operations」新增更新数量 / 切换勾选态两个端点场景;「Order lifecycle」新增显式 `items` 直接建单(绕开购物车)场景

## Impact

- **后端**:`com.seafood.order.domain.Cart`/`CartItem`(新增 `updateQuantity`/`toggleSelected`)、`CartService`、`CartController`、`OrderService#create`、`OrderController#create`、`com.seafood.user.domain.Address`(新增 `district`)及对应 Repository/Document/DTO
- **前端**:`frontend/app.js`(`onLaunch` token 桥接)、`frontend/pages/cart/cart.wxml`、`frontend/pages-sub/user/address/address-list.wxml`(`detailAddress` → `detail`)
- 无 BREAKING:`POST /api/orders` 的 `items` 是可选字段,不传时行为与现在完全一致;`Address.district` 是新增字段,现有无 `district` 的历史地址文档按 Java record 默认值/迁移策略处理(design.md 中明确)
- 不涉及:User 域会员运营字段(积分/VIP/余额等,另开 change `user-membership-domain`)、mp-07 地址卡片布局(另开 change `mp-address-card-layout`)、跨屏命名清理(另开 change `mp-cross-screen-cleanup`)
- 不涉及(新发现的范围外缺口,已记入本 change 遗留问题):`CartItem`/`OrderItem` 域对象目前都不带 `skuId`——`product-sku` spec 已支持商品挂多 SKU,但购物车/订单管线完全没有 SKU 感知,一件多 SKU 商品加入购物车后会丢失具体选中的是哪个 SKU。本 change 的 direct-buy `items` 只做 `{productId, quantity}`,不解决这个更深的 SKU-in-order 缺口,避免搭车扩大改动面
- 不涉及(Task 2 研究阶段新发现的范围外缺口,已记入本 change 遗留问题):`Order` 域对象完全没有 `addressId`/`shippingAddress`/`remark` 字段。`OrderController#create` 目前无 `@RequestBody` 参数,前端 `order-confirm.js` 收集的收货地址选择 + 备注(`orderStore.placeOrder({addressId, remark})`,`CreateOrderRequest` 类型早已定义这两个字段)发到 `POST /api/orders` 后被 Spring 静默丢弃——订单从未真正记录收货地址或备注。这是独立于本 change 4 个 gap 的另一个真 bug,量级不小(涉及 `Order` 聚合新增字段 + 迁移 + 可能影响 admin 发货流程),本 change 只新增 `items` 字段,不顺带修复,避免范围蔓延;建议另开 change
