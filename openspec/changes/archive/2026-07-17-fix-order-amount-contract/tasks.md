## 1. 测试先行(TDD)——后端

- [x] 1.1 `OrderService` 单测:`create(userId, ...)` 传 `shippingMethod=SF`(顺丰,¥12)+ 购物车小计 ≥100,断言 `totalAmount = subtotal + 12 - 10`
- [x] 1.2 单测:`shippingMethod` 缺省/null,断言按 `FREE`(shippingFee=0)兜底
- [x] 1.3 单测:小计 < 100,断言 `discount = 0`
- [x] 1.4 单测:直接购买路径(`create(userId, items, shippingMethod)`)同样接收并应用 `shippingMethod`
- [x] 1.5 回归用例:退款金额上限校验(`amount ≤ totalAmount`)、`orders.paid` amountBucket 分桶、CSV 导出金额列——这些用例构造 `OrderDocument` 都直接 `setTotalAmount(...)`,不经过 `persistOrder`,不受定价逻辑改动影响,原样通过;另有 3 条**直接调用 `service.create(...)` 的既有测试**因小计恰好命中"满 100 减 10"阈值,预期 `totalAmount` 从 100.00 改为 90.00(`create_snapshotsPriceAndDecrementsStock`/`create_withExplicitItems_buildsOrderWithoutTouchingCart`/`create_withEmptyItemsList_fallsBackToCartPath`,均已更新并注释原因)。**额外发现**:OpenAPI 契约漂移门(`OpenApiContractIT`)因新增响应字段红了一次,按既定流程 `CONTRACT_UPDATE=true ./gradlew test --tests *OpenApiContractIT` 重生成 `openapi.json` SoT

## 2. 修复实现——后端

- [x] 2.1 `CreateOrderRequest` 新增可空 `shippingMethod` 字段
- [x] 2.2 `Order` 聚合 / `OrderDocument` / `OrderResponse` 新增 `subtotal`、`shippingFee`、`discount` 三个字段(均按 design.md 决策 1"为方便订单详情展示明细一并持久化";`Order` 紧凑构造器对 null 按 0 兜底,覆盖历史订单读取——决策 3)
- [x] 2.3 新增 `OrderPricing`(`com.seafood.order.application`,同 `OrderMetrics` 风格的无状态工具类):运费查表常量(FREE=0/SF=12/ZTO=8,与 mp `SHIPPING_FEE_MAP` 数值对齐)+ 满 100 减 10 规则函数,`OrderService.persistOrder` 计算 `totalAmount = subtotal + shippingFee - discount`
- [x] 2.4 `OrderService.create` 系列新增 3 参重载(`create(userId, paymentMethod, shippingMethod)` / `create(userId, items, shippingMethod)`),旧签名委托新签名(`shippingMethod=null`),`persistOrder`/`OrderController` 全链路补传;**额外发现的连锁改动**:`Order` record 加 3 个位置参数直接破坏其positional constructor 的全部既有调用点(`OrderMapper`/`OrderBuilder` 测试 fixture / `OrderTest`/`OrderTrackingTest`/`OrderResponseJsonTest` 里 7 处裸 `new Order(...)`),`OrderResponse` 同理破坏 `AdminBffServiceTest`/`OrderControllerSliceTest` 里 5 处裸 `new OrderResponse(...)`,以及 `OrderControllerSliceTest` 两条 mock 断言因 Controller 改调 3 参重载需要换 Mockito matcher(`anyString()`/`anyList()`区分两个 3 参重载)——全部已同步修
- [x] 2.5 跑 1.1-1.5 用例转绿(`OrderServiceTest`/`OrderTest`/`OrderTrackingTest`/`OrderResponseJsonTest`/`OrderControllerSliceTest`/`AdminBffServiceTest` 共 106 例全过)

## 3. 测试先行(TDD)——前端

- [x] 3.1 mp Jest:`order-confirm.js` 的 `SHIPPING_FEE_MAP`/`calcDiscount` 数值与后端新增常量断言一致——FREE=0/SF=12 此前已有测试覆盖,补了 ZTO=8 的用例补全三档;后端 `OrderPricing` 三个常量数值与 mp 侧逐一核对一致(锁定不漂移,两处独立测试各自断言相同数字)
- [x] 3.2 mp Jest:提交时 `POST /api/orders` 请求体包含 `shippingMethod`——改写 `onSubmitOrder` 的 3 条既有成功路径断言(购物车结算/direct-buy/direct-buy 空数组兜底 FREE),先实测 RED(当前请求体缺这个字段)
- [x] 3.3 mp Jest:新增用例断言订单创建成功后 `subtotal`/`shippingFee`/`discount`/`orderTotal` 从后端 `OrderResponse` 覆盖本地预估值(故意把本地预估设成明显不同的假值 999,证明确实被覆盖而非碰巧一致)。**范围澄清**:全仓没有独立的"下单成功页"路由——`order-confirm.js` 提交成功后原地渲染再重定向到 order-list,是唯一存在本地预估→需要切换展示源的页面;`order-list.js`/`order-detail.js` 从 `GET /api/orders`/`GET /api/orders/{id}` 拿数据渲染 `item.totalAmount`/`order.totalAmount`,本来就是读后端字段,不需要改代码

## 4. 修复实现——前端

- [x] 4.1 `order-confirm.js` 的 `onSubmitOrder` 两条 `placeOrder`/`placeDirectBuyOrder` 请求体都补 `shippingMethod: this.data.shippingMethod`
- [x] 4.2 `order-confirm.js` 的 `placeOrderPromise.then((order) => {...})` 成功回调补 `subtotal`/`shippingFee`/`discount`/`orderTotal` 四个字段的 `setData`,从 `order.subtotal`/`order.shippingFee`/`order.discount`/`order.totalAmount` 取值;`order-list`/`order-detail` 确认无需改动(见 3.3);`frontend/src/features/order/types.ts` 的 `Order`/`CreateOrderRequest` 补对应字段类型(`.js` runtime shim 不需要同步——`OrderAPI.create`/`orderStore.placeOrder` 都是透传 body,不解构新字段)
- [x] 4.3 跑 3.1-3.3 用例转绿(`order-confirm.test.js` 49/49 + `order-confirm-wxml-contract.test.js`,`npm run type-check` 通过)

## 5. E2E 验收(零 mock)

- [x] 5.1 本机无 mp DevTools 会话,改用零 mock curl 直连真实部署后端(重建镜像跑最新代码)验证:真实 `/api/auth/wechat-login` 登录 → 真实 `POST /api/orders`(`shippingMethod=SF` + 商品价 145.11 ≥100)→ `subtotal=145.11, shippingFee=12, discount=10, totalAmount=147.11`,与 `subtotal+shippingFee-discount` 手算一致
- [x] 5.2 复测边界:①不传 `shippingMethod` + 小计 23.82<100 → `shippingFee=0, discount=0, totalAmount=23.82`(FREE 兜底 + 无优惠皆正确);②客户端请求体里塞 `"totalAmount":0.01` 试图覆盖金额 → 后端完全忽略,仍按 items+shippingMethod 权威算出 `totalAmount=31.82`(spec.md "Client-submitted total amount is ignored" scenario 实测通过)
- [x] 5.3 更新 memory `mp-e2e-fullstack-2026-07-13.md`(状态表 5→✅完整 + 新增详细实现记录小节,含 Order record 加字段炸出的连锁改动、OpenAPI 契约漂移门首次撞见、mp-06 视觉 A/B 对比结论)

## 6. 回归

- [x] 6.1 `./gradlew test` 全量通过——693/693,0 失败(688→692 实现阶段 + `/opsx:verify` 阶段补的 `OrderControllerSliceTest.create_withClientSubmittedTotalAmountField_ignoredNotErrorNotUsed` 锁死 spec.md "Client-submitted total amount is ignored" scenario → 693);`./gradlew check`(ArchUnit + checkNoRefreshScope)通过;`OpenApiContractIT` 2/2 确认无再次漂移
- [x] 6.2 `cd frontend && npm test` 全量通过——80 个 suite / 751/751 例,0 失败(749→751,与本次新增的 2 条 mp 用例数一致);`npm run type-check` 通过
- [x] 6.3 `npm run test:visual mp-06-order-confirm` diff 32.72%(RED,阈值 5%)/ `npm run test:geometry mp-06-order-confirm` 5/5 GREEN。**做了改动前后 A/B 对比排除回归疑虑**:临时把 `order-confirm.js` 还原到本 change 之前的版本重跑感知层,diff% 完全相同(32.72%=32.72%)——证明这是既有的 OD 视觉偏离基线(本次改动只碰了提交请求体字段 + 提交成功后的 `.then()` 回调,未碰 wxml/wxss/recalcAmounts,预提交态的截图渲染逻辑一字未动),不是本次改动引入的回归,不阻塞
