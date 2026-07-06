## Why

`mp-od-prototype-alignment`(已归档,2026-07-04)的最终跨屏一致性 review 记录了 6 条 Minor 命名/风格不统一问题,外加一条"可以考虑"的重构建议(`order-list.js`/`order-detail.js` 抽取共享 order-action controller——这正是该次 review 抓到的 `err.status`/`err.statusCode` 分叉 bug 的根源:一份拷贝改了另一份没跟上)。本 change 集中处理,顺带做完整的 wxml `bindtap` 命名审计(过程中发现原笔记记录的范围偏窄)。

## What Changes

- **返回按钮命名统一**:`goBack`(order-confirm.js/address-list.js)→ 统一改 `onBack`,与 order-list/order-detail/product-detail 三屏一致
- **bindtap handler 裸动词 → onXxx 前缀**(完整审计后确认的范围,比原笔记记录的更广):
  - `address-list.js`:`selectAddress`/`editAddress`/`deleteAddress`/`addNewAddress`/`setDefaultAddress` → `onSelectAddress`/`onEditAddress`/`onDeleteAddress`/`onAddNewAddress`/`onSetDefaultAddress`
  - `cart.js`:`selectAddress`(bindtap 在 `cart.wxml:37`)→ `onSelectAddress`
  - `index.js`/`category.js`:`addToCart`(两屏各自定义、行为特意保持一致)→ `onAddToCart`
  - `product-detail.js`:`goToProductDetail`(推荐商品卡片跳转)→ `onGoToProductDetail`
- **`order-detail.wxss` 改用 BEM 命名**(`block__element--modifier`),替换现有扁平缩写类名(`.nm`/`.pr`/`.act`/`.card-title` 等),与 `order-list.wxss` 的既有 `.order-card__xxx` 风格对齐
- **地址卡片 class 名统一**:`cart.wxml`/`cart.wxss` 的 `cart-address` block → `address-card`,与 `order-confirm.wxml`/`address-list.wxml` 已在用的 `address-card` 对齐
- **清理 no-op JSON 配置**:`order-confirm.json`/`address-list.json` 在 `navigationStyle: "custom"` 下,`navigationBarBackgroundColor`/`navigationBarTextStyle` 对原生导航栏的配置已无实际效果(自定义导航栏替代了原生栏),删除
- **money 格式化收敛成共享 util**:新增 `frontend/utils/money.js`(或 `src/shared/utils` 下,视既有 shared/utils 惯例定),导出 `roundYuan(amount)`;`order-confirm.js` 的本地 `roundYuan()` 和 `cart.js` 的裸 `.toFixed(2)` 都改用这个共享实现
- **抽取共享 order-action controller**:`order-list.js`/`order-detail.js` 里近乎逐行重复的订单操作分发逻辑(pay/cancelOrder/remindShip/reorder/deleteOrder 等 ~120 行)抽成一个共享模块,两个页面都改为调用它。**行为副作用(非纯重构,实为补齐已批准 spec 的缺口)**:抽取时发现 `order-list.js` 的"申请退款"(`requestRefund`/`afterSale`)目前只弹一个"退款功能开发中,Sprint 3 上线"的占位提示,而 `openspec/specs/mini-program/spec.md`「Order list and detail (mp-08) customer action row」requirement 早已明确要求 `PAID`/`COMPLETED` 状态下"申请退款"/"申请售后" MUST 调 `POST /api/orders/{id}/refund`——`order-detail.js` 的等价操作(`applyRefund`)已经这样做了,`OrderAPI.requestRefund()` 这个方法本身也早就存在,只是 order-list.js 没接上。这不是新增行为,是让 order-list 也符合已批准 spec、追平 order-detail 已经做到的合规状态

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `mini-program`:「Order list and detail (mp-08) customer action row」requirement 补一条显式约束(两屏行为不得分叉)+ 一条可测试场景(从任一屏发起退款都必须真实调用 `POST /api/orders/{id}/refund`),把此前只靠"两处代码碰巧一致"隐式维持的约束落成文字

## Impact

- **前端**:`frontend/pages-sub/user/address/address-list.js`+`.wxml`+`__tests__/*`、`frontend/pages/cart/cart.js`+`.wxml`+`.wxss`+`__tests__/*`、`frontend/pages/index/index.js`+`.wxml`+`__tests__/*`、`frontend/pages/category/category.js`+`.wxml`+`__tests__/*`、`frontend/pages-sub/product/product-detail/product-detail.js`+`.wxml`+`__tests__/*`、`frontend/pages-sub/order/order-confirm/order-confirm.js`+`.wxml`+`__tests__/*`、`frontend/pages-sub/order/order-detail/order-detail.js`+`.wxss`+`.wxml`+`__tests__/*`、`frontend/pages-sub/order/order-list/order-list.js`+`__tests__/*`、新增共享模块(order-action controller + money util)
- 无 BREAKING:纯前端内部重构/重命名,不改变任何后端 API 契约;order-list 的退款行为变化是"让已存在的能力生效",不是破坏性变更(用户体验从"假占位"变成"真提交",不会有任何已依赖旧占位行为的调用方)
- 不涉及(研究阶段新发现,记入本 change 遗留问题,不在此修):`order-confirm.js#selectAddress` 方法完全是死代码——wxml 的地址卡片用原生 `<navigator url="...">` 直接跳转,从未 bindtap 到这个方法,该方法此前疑似是更早版本的产物;`cart.js` 从地址选择页手动选完地址回跳后,`prevPage.selectedAddressFromList` 从未被 `cart.js` 读取(另一处独立死绑定,cart.js 文件头注释已自行记录此缺口但未修)。这两个都是真实功能缺口而非命名/风格问题,不属于本次清理范围,建议另开 change 处理
