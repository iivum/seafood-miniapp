## 1. 购物车:数量更新 + 勾选切换端点(Gap 1)

- [x] 1.1 `backend/src/test/java/com/seafood/order/domain/CartTest.java`:TDD RED — 新增 `updateQuantity(productId, quantity)` 替换指定行数量、`toggleSelected(productId)` 翻转指定行 `selected` 的用例;各补一条"productId 不在购物车里"抛异常的用例(设计 D1:新增查找失败异常,非静默 no-op)
- [x] 1.2 `com.seafood.order.domain.Cart`:实现 `updateQuantity`/`toggleSelected`,行不存在时抛 `CartItemNotFoundException`(独立 `RuntimeException` 子类,与既有 `AccountLockedException`/`RateLimitedException` 先例一致 —— 与 D1 一致,且避免和"quantity ≤ 0"校验失败共用一个异常类型导致 Service 层无法精确区分 404 vs 409);跑 1.1 测试 GREEN
- [x] 1.3 `backend/src/test/java/com/seafood/order/application/CartServiceSliceTest.java`:TDD RED — `CartService#updateQuantity`/`toggleSelected` 把领域异常转译成 `NotFoundException`(参照 `AddressController#get` 的 404 模式)
- [x] 1.4 `com.seafood.order.application.CartService`:实现 `updateQuantity(userId, productId, quantity)`/`toggleSelected(userId, productId)`,复用 `persist`/`toResponse` helper;跑 1.3 GREEN
- [x] 1.5 `backend/src/test/java/com/seafood/order/api/CartControllerSliceTest.java`:TDD RED — `PUT /api/cart/items/{productId}` body `{quantity}` 200、行不存在 404;`PATCH /api/cart/items/{productId}` 无 body 200、行不存在 404
- [x] 1.6 `com.seafood.order.api.CartController`:新增 `@PutMapping("/items/{productId}")`/`@PatchMapping("/items/{productId}")` 路由 + 新建 `CartQuantityUpdateRequest`(只带 `@Positive int quantity`,不复用 `CartItemRequest` 避免路径参数与 body 里的 productId 重复);跑 1.5 GREEN
- [x] 1.7 `cd backend && ./gradlew test --tests "*Cart*"` 全绿(30/30),确认无回归 —— commit `ade2df2`,task-reviewer 一次通过(spec ✅ / quality Approved,0 Critical/Important)

## 2. 直接购买建单绕开购物车(Gap 2)

- [x] 2.1 `backend/src/test/java/com/seafood/order/application/OrderServiceTest.java`:TDD RED — `create(userId, items)` 用显式 `items` 建单时不读购物车、不清空购物车,校验/库存扣减/订单落库与现有购物车路径行为一致;新增"items 传空 list 时回退到购物车路径""items 某行库存不足返回 409 且不动购物车"两条用例
- [x] 2.2 `com.seafood.order.application.OrderService`:抽取共享私有 helper `validateAndDecrementLines`(逐行校验商品存在/上架/库存 + 扣减)+ `persistOrder`/`recordOrderCreatedMetric`(设计 D3),`create` 新增重载接受 `List<CartItemRequest> items`(null/empty → 现有购物车路径;非空 → 直接用 items 建单,不碰 `carts` repository);跑 2.1 GREEN。**首轮 review 抓到 1 个 Important 回归**:抽取时误把购物车路径的"商品是否存在"校验收窄成只查 `selected` 行(此前是查全部行,未选中行指向已下架商品本该整单校验失败,重构后被静默放过)——用两个独立参数 `existenceCheckLines`(全部行)/`linesToBuild`(仅 selected)拆开,恢复重构前语义,补回归测试 `create_cartHasUnselectedItemPointingAtMissingProduct_failsWholeCheckout`,re-review Approved(commit `7192d22` + fix `1c52eb0`)
- [x] 2.2b `backend/src/main/java/com/seafood/order/api/dto/CreateOrderRequest.java`(新建):`{ items: List<CartItemRequest> }`,`items` 可为 null
- [x] 2.3 `com.seafood.order.api.OrderController#create`:接受可选 `@RequestBody(required = false) CreateOrderRequest`,按设计 D3 分支调用
- [x] 2.4 `backend/src/test/java/com/seafood/order/api/OrderControllerSliceTest.java` 补 2 条用例:带 `items` body 201 且购物车不受影响;不带 body(或 body 为空对象)行为与今天完全一致
- [x] 2.5 `frontend/src/features/order/types.ts`:`CreateOrderRequest` 新增可选 `items?: Array<{productId: string; quantity: number}>`;确认 `OrderAPI.create()` 原样透传 body,无需改动
- [x] 2.6 `frontend/src/features/order/store.ts`/`.js`:新增 `placeDirectBuyOrder(body)` 方法 —— 调 `OrderAPI.create(body)`,**不** 调用 `cartStore.clear()`(design D3b:direct-buy 从未碰过购物车,没有可清的东西;`.ts`/`.js` 两份同步更新,`placeOrder()` 保持不变)
- [x] 2.7 `frontend/pages-sub/product/product-detail/product-detail.js`(mp-03「立即购买」`onBuyNow`):停止调用 `cartStore.addItem()`;改为构造 `items = [{productId: product.id, quantity: this.data.quantity || 1}]`,导航到 `.../order-confirm?source=direct_buy&items=<encoded>`(设计 D3b:同 `order-confirm.js#selectAddress` 已有的 `encodeURIComponent(JSON.stringify(...))` URL query 先例);测试断言翻转为"不再调用 cartStore.addItem";文件头"已知缺口"注释同步更新为已修复
- [x] 2.8 `order-confirm.js#onLoad`(design D3b):分支处理 `options.source === 'direct_buy'` —— 解析 items,逐行调 `ProductAPI.getById(productId)` 补齐 name/price/imageUrl,`setData` 到 `cartItems`(与 `refreshCartPreview` 同形状,`recalcAmounts()` 读的是这个字段不是 `order.items`)+ `directBuyItems`;跳过 `cartStore.refresh()`,仍调用 `autoSelectDefaultAddress()`。非 direct_buy 分支行为不变
- [x] 2.9 `order-confirm.js#onSubmitOrder`:`this.data.directBuyItems` 非空时调 `orderStore.placeDirectBuyOrder({items, addressId, remark})`;否则维持现有 `orderStore.placeOrder({addressId, remark})` 不变
- [x] 2.10 后端 `./gradlew test --tests "*Order*"` + 前端 `npm test -- product-detail order-confirm order/store` 全绿(2a commit `7192d22`+`1c52eb0`,2b commit `e3dc7c5`,均 task-review 通过)

## 3. Address `district` 字段补全(Gap 3)

- [x] 3.1 `backend/src/test/java/com/seafood/user/domain/AddressTest.java`(若不存在则新建):TDD RED — `Address` record 携带 `district`,构造/getter 断言
- [x] 3.2 `com.seafood.user.domain.Address`:新增 `district` record 组件(`city` 之后、`detail` 之前,与 spec 字段顺序一致);跑 3.1 GREEN,顺带修所有因构造器多一个参数报编译错的调用点(`UserService#addAddress`/`#updateAddress` 等)
- [x] 3.3 `backend/src/test/java/com/seafood/user/api/dto/AddressUpsertRequestTest.java`(若不存在则新建):TDD RED — `toAddRequest()`/`toUpdateRequest()` 把 `district` 直接透传,不再折进 `detail`(替换旧的 `foldedDetail()` 折叠断言)
- [x] 3.4 `com.seafood.user.api.dto.AddAddressRequest`/`UpdateAddressRequest`:新增 `district` 字段;`AddressUpsertRequest`:删除 `foldedDetail()`,`toAddRequest()`/`toUpdateRequest()` 直接传 `district`;跑 3.3 GREEN
- [x] 3.5 `backend/src/test/java/com/seafood/user/application/UserServiceTest.java`:补 1 条用例 —— `addAddress`/`updateAddress` 传入 `district` 后,读回的 `Address.district()` 与传入值一致(锁住"编辑页地区选择器现在能回填"这条修复)
- [x] 3.6 `com.seafood.user.application.UserService`:`addAddress`/`updateAddress` 构造 `Address`/patch 时带上 `req.district()`;跑 3.5 GREEN
- [x] 3.7 `frontend/pages-sub/user/address/address-list.wxml:59`:`{{item.detailAddress}}` → `{{item.detail}}`(`district` 已正确引用,不用改)
- [x] 3.8 `frontend/pages/cart/cart.wxml:44`:同上,`detailAddress` → `detail`
- [x] 3.9 `frontend/pages-sub/user/address/__tests__/address-list-wxml-contract.test.js` + `frontend/pages/cart/__tests__/cart.test.js`:更新/新增断言锁住 `detail` 字段名(替换任何断言旧 `detailAddress` 渲染的用例)
- [x] 3.10 `backend/seed/fixtures/users.json`:给现有种子地址补一个 `district` 值(如 `"陆家嘴街道"`),让 dev/C5 视觉验证数据真实
- [x] 3.11 `cd backend && ./gradlew test --tests "*Address*" --tests "*UserService*"` + `cd frontend && npm test -- address cart` 全绿

## 4. app.js 冷启动 token 桥接(Gap 4)

- [x] 4.1 `frontend/app.test.js`(新建,此前完全没有 app.js 测试):TDD RED —— mock `tokenStorage.getAccessToken()` 返回一个 token,`onLaunch()` 后断言 `app.globalData.token` 等于该值;另一条用例 mock 返回 `null`,断言 `globalData.token` 为 `null`(不抛异常)
- [x] 4.2 确认 `frontend/src/shared/api/storage.js` 此前不存在(又一处 shim-drift)。新建 `storage.js`(只实现被消费的 `getAccessToken`,同 `user/api.js` 先例);新建 `storage.runtime.test.js`,`require('./storage.js')`(带扩展名)验证行为,4/4 GREEN,100% 覆盖率
- [x] 4.3 `frontend/app.js`:`onLaunch` 新增一个独立 try/catch 桥接(设计 D5):`require('./src/shared/api/storage.js')` 拿 `tokenStorage`,`this.globalData.token = tokenStorage.getAccessToken()`;`checkLoginStatus`/`validateToken` 完全未动(legacy,范围外);跑 4.1 GREEN
- [x] 4.4 真实 E2E 验证(weapp-dev MCP + miniprogram-automator 直连,因 MCP 工具面没有"在 app 上下文里 evaluate 任意 JS"能力):在真实 WeChat DevTools 里对真实、未改动的 `cart.js#autoSelectDefaultAddress()` 拦截 `wx.request` 抓 Authorization 头 —— 桥接前 `globalData.token=null` 时头恒为 `null`,重新调用 `onLaunch()` 桥接后头精确等于 `Bearer <bridged token>`,直接实锤了 `utils/request.js` 的鉴权门槛此前确实会静默漏 token。受限于本 sandbox 会对经过任何环节(文件/进程内 HTTP 响应)的 JWT 形状字符串做透明脱敏,未能拿到一次完整 200 OK 真实地址回显(token 有效性本就在 D5 范围外);过程中順带发现 2 个后端基础设施问题(见遗留问题清单)
- [x] 4.5 `cd frontend && npm test -- app storage` 全绿(551/551,54 suites)—— commit `9f3f5ef`,controller 直接复核(dispatch 的 task-reviewer 撞到 API session 限额未产出结果),spec ✅ / quality Approved,0 Critical/Important

## 5. 收尾

- [x] 5.1 `openspec validate mp-backend-contract-gaps --strict` 通过
- [x] 5.2 `cd backend && ./gradlew check` 全绿(含 ArchUnit + checkNoRefreshScope)
- [x] 5.3 `cd frontend && npm test` 全绿,无回归
- [x] 5.4 遗留问题清单已更新(见下方「遗留问题清单」小节)
- [x] 5.5 `/opsx:archive mp-backend-contract-gaps`(全部任务 + spec 同步后)

## 遗留问题清单(本 change 范围外,供后续 change 参考)

- **`CartItem`/`OrderItem` 域对象不带 `skuId`**(proposal.md 已记录,Task 2a/2b 研究阶段确认):`product-sku` spec 已支持商品挂多 SKU,但购物车/订单管线完全没有 SKU 感知,一件多 SKU 商品加入购物车会丢失具体选中的是哪个 SKU。本 change 的 direct-buy `items` 只做 `{productId, quantity}`,不解决这个更深的 SKU-in-order 缺口。
- **`Order` 域对象完全没有 `addressId`/`shippingAddress`/`remark` 字段**(proposal.md 已记录,Task 2a 研究阶段发现):`OrderController#create` 此前无 `@RequestBody`,前端 `order-confirm.js` 收集的收货地址选择 + 备注(`CreateOrderRequest` 类型早已定义这两个字段)发到 `POST /api/orders` 后被 Spring 静默丢弃——订单从未真正记录收货地址或备注。量级不小(涉及 `Order` 聚合新增字段 + 迁移 + 可能影响 admin 发货流程),本 change 只新增 `items` 字段,不顺带修复。
- **`seafood-backend:native` 在 arm64 主机上报 `exec format error`**(Task 4 E2E 验证时发现):`docker image inspect` 明明报告镜像是 `arm64`,但容器启动即崩,只能退回 `seafood-backend:jvm`(项目 CLAUDE.md 已有此先例记录,但 native 镜像本身的 arch 不匹配问题从未被修过)。
- **`backend/seed/seed.sh` 的 `categories.json` 导入步骤有 bug**(Task 4 E2E 验证时发现):`Performing an update on the path '_id' would modify the immutable field '_id'` 报错,在 `set -e` 下会中断脚本剩余步骤(products/users/banners 导入从未真正跑到,除非手动跳过 categories 步骤单独执行)。
- **本地环境 `eslint` 损坏**(Task 2b / Task 4 均确认):`node_modules/.bin/eslint` 抛 `Cannot find module '../package.json'`,对任意文件(包括完全未改动的文件)都复现,与本 change 改动无关,是环境本身的问题。
- **（Minor,最终全分支 review 发现,未阻塞归档）`app.js` 混合新旧登录态下有一条极窄的异步覆盖路径**:`onLaunch` 里 `checkLoginStatus()`(legacy,4.3 明确未改动)先于新桥接执行,若某用户 storage 里同时存在 legacy `token` key 和新版 `accessToken` key(理论上只有"曾用旧版登录、后来又用新版登录"这种历史混合态才会出现),`checkLoginStatus` 触发的 `validateToken()` 对不存在的 `/auth/me` 发请求,其 `.catch` 在 `onLaunch` 返回之后才跑,会把刚桥接好的 `globalData.token` 覆盖回 `null`。不影响本 change 修复的主场景(纯新版登录只写 `accessToken`,`checkLoginStatus` 的 `if(token)` 为假,不会触发);且该 `.catch` 自带 `removeStorageSync('token')`,下次冷启动即自愈。已记录,不建议单独开 change,后续如做 legacy 登录路径退休时一并清理。

## 全分支 review 结论

**批准(Approved)**。9 commit 全量 diff 复核:12 条 delta spec 场景逐条对照实现代码全部满足;Gap 1/Gap 2 触碰的文件互不冲突、Gap 2 首轮 review 修的回归在最终状态里确认没有被后续 commit 带回;`5e1ef4e`(此前唯一没走 task-review 的 commit)确认是纯生成产物,逐字段核对与 4 个 gap 实际实现一致;proposal.md「不涉及」清单 + 遗留问题清单逐项核实均未被偷偷绕过或部分修掉;后端 601 测试 0 失败(ArchUnit + checkNoRefreshScope 含在内)、前端 551 测试全绿。0 Critical/Important,1 Minor(见上方遗留问题清单最后一条)。
