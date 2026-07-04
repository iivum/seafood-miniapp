## 1. mp-01 home ✅

- [x] 1.1 诊断：跑 `npm run test:visual mp-01-home` + `npm run test:geometry mp-01-home`，记录当前感知 diff% + 几何层各项状态 + 偏离区域。结果：感知 66.99% RED（比 C5 的 61% 还差）；几何层 4/4 GREEN（banner/category-row/section-header/grid-columns）
- [x] 1.2 对照 golden + diff 图列偏离点：顶部定位/搜索栏整块缺失、filter chip 行整块缺失、hero banner 视觉语言完全不同（浅色小卡片 vs OD 深色沉浸大卡片带 LIVE/价格/CTA）、品类导航从圆形图片图标变成扁平文字 chip、section header 文案/查看全部链接缺失。发现现有 spec 里 mp-01 requirement 文字描述本身与 OD golden 不符（过时）
- [x] 1.3 写 task brief（`.superpowers/sdd/mp-od-1-home-brief.md`），派 implementer subagent 修复（commit `3e47552`：新增顶部栏+搜索复用死代码、banner 前端近似深色重样式、修复分类硬编码 4→5 真实数据 bug、filter chip 行仅"全部"功能性、section header 动态文案，34 个新测试，327/327 全绿）；task reviewer 复查：spec 6/6 符合，代码质量 Approved（1 条 Important：`.home-chip` class 语义偷换影响 geometry 检查）
- [x] 1.4 复验：控制器直接修了 reviewer 指出的 geometry selector 问题（`category-row` 改指向 `.home-category`，新增 `filter-chip-row` 检查），复验时额外发现 banner tone class 大小写不匹配的真 bug（wxml 绑定后端大写枚举 `ACCENT`/`SOFT`，CSS 写小写 `--accent`/`--soft`，导致深色样式从未生效），已修（commit `e645a8d`）。最终：几何层 5/5 GREEN；感知层 66.99%→56.26%，剩余差距逐项核对为预期内容差异（OD mockup 冻结文案/图片 vs 真实 seed 数据：banner 文案、产品图、"55 款"vs"9 款"），非结构/样式偏差
- [x] 1.5 commit 完成（`3e47552` + `e645a8d`），ledger 见下方

## 2. mp-02 category

- [x] 2.1 诊断：感知 66.72% RED；几何层 3/3 GREEN（sidebar/header/grid-columns）
- [x] 2.2 对照 golden 发现：OD 原型实际是"11 类目 + Featured banner + 人气TOP6 + 本季新品"编辑推荐型页面，与现有"5 类目侧栏筛选网格"架构不同。**已与用户确认范围**：只做前端近似，保留现有 5 类目架构，不新增这 3 个板块，不扩展类目数
- [x] 2.3 写 task brief（`.superpowers/sdd/mp-od-2-category-brief.md`），派 implementer 修复（commit `b2733f1`：新增顶部"分类"标题栏+装饰搜索按钮、侧栏从纵向堆叠改横向单行文字、修复 token 违规，顺带修 2 个真 bug——`activeCategoryId`/`selectedCategory` 死绑定 + `data-category-id`/`data-id` 不匹配导致侧栏点击失效，328/328 测试全绿）；task reviewer 复查：spec ✅ 范围收敛干净（未偷加越界板块），代码质量 Approved（0 Critical/Important，2 Minor 非阻塞），2 个顺带 bug 独立核实为真
- [x] 2.4 复验：几何层 3/3 GREEN；感知层 66.72%→61.02%，剩余差距是架构性的（OD 有 Featured/TOP6/新品板块，按约定未做），非新 bug
- [x] 2.5 commit 完成（`b2733f1` + 复验截图 commit），ledger 已更新

## 3. mp-03 product-detail

- [x] 3.1 诊断：感知 63.22% RED；几何层 4/4 GREEN（product-image/product-name/product-price/footer-bar）
- [x] 3.2 对照 golden 发现：原价划线、物流/质量标签行（捕捞当日/顺丰冷链/死蟹包赔）、统计行（规格/冰鲜链/评价数）后端 `Product` 无对应字段，按已定原则不编造、跳过；确认单图 `<image>`（非 swiper）是对的，spec 里"3-5 张轮播"是过时描述
- [x] 3.3 写 task brief（`.superpowers/sdd/mp-od-3-product-detail-brief.md`），派 implementer 修复（commit `c10c093`：新增悬浮顶栏返回/收藏/分享、token 核对）；implementer 中途发现并经确认后顺带修复 2 个范围外真 bug——① 数量 stepper 死绑定（`onIncrement`/`onDecrement` 从未定义）② `onBuyNow` 重复定义导致违反已有 spec requirement「Direct buy from product detail」（91-107 行），已恢复正确跳转目标（mp-06 而非购物车）。**已知缺口**：完全满足该 spec 的"mp-06 不碰购物车+items 只显示这一个商品"仍需要后端 `POST /api/orders` 支持显式 items 字段（当前只能从用户购物车建单），超出前端范围未做，记入本 change 遗留清单（见文末）。360/360 测试全绿；task reviewer 复查：spec ✅ 范围收敛干净（grep 确认零处碰 backend/），代码质量 Approved（0 Critical/Important，3 Minor：死 class、scrim rgba 无 token 注释、报告标题措辞夸大）
- [x] 3.4 复验：控制器清理 2 处 Minor（删死 class + 补注释说明既定例外），几何层 4/4 GREEN 不变；感知层 63.22%→62.72%，残差为已确认的架构性差异
- [x] 3.5 commit 完成（`c10c093` + `d65117b`），ledger 已更新

## 4. mp-04 cart

- [x] 4.1 诊断：发现测试 harness 本身的 bug——`visual-diff.cjs` 的 `mp-04-cart` 条目缺 `auth:true`（geometry fixture 本来就有），导致截到登录页而非购物车，假信号。已单独修复（commit `c4e86cf`）。修复后重新诊断：几何层 4/4 GREEN（address-card/select-bar/item-rows/checkout-bar）；感知层先是拿到假信号，修完 harness 后拿到真信号——发现更严重的**生产级真 bug**：`CartService.get()` 从不返回商品名/价格/图片（`CartItem` 域对象只有 `productId/quantity/selected/addedAt`），任何用户打开购物车都是原始 ID + ¥0。已与用户确认纳入本 change 修复
- [x] 4.2 对照 golden 发现除数据 bug 外，还缺顶部"购物车(N)"标题栏、地址卡未自动选中默认地址；划线原价/"海港直营"商家分组标签（单卖家系统概念不成立）/"常一起买"推荐区/SKU chip 按已定原则跳过
- [x] 4.3 **两轮修复**：
  - 第一轮（后端富化，commit `5997e88`）：`CartService` 注入 `ProductService`（仿 `BannerService` 跨模块查询先例），新增 `CartLineItemResponse` 富化 DTO，字段名对齐前端 fallback 链（`productName`/`unitPrice`/`imageUrl`），商品不存在时单行降级不 500。OpenAPI 契约同步重生成。task reviewer 独立重跑 `./gradlew check --rerun-tasks`：600/600 测试 + ArchUnit 4/4 全过，Approved
  - 第二轮（样式 + 前端 bug，commit `fe492ec`）：新增标题栏、默认地址自动选中（真 bug：`selectedAddress` 从未自动查询过默认地址）、以及**顺带修复的重大死绑定**——`cart.wxml` 引用的 `isItemSelected(item.id)`（WXML 不支持函数调用表达式）/`onItemCheckTap`/`onSelectAllTap` 在 `cart.js` 从未定义，checkbox/全选完全无响应，"选品→结算"核心链路被卡死（2026-06-16 v2-visual-redesign 重构引入，此后从未被发现）。改绑 `item.selected` + 补齐 handler + `reconcileSelection()`（纯前端驱动，因为对应后端 PATCH 端点不存在）。task reviewer 逐行走查 `reconcileSelection` 时序正确性，确认真实无漏洞，Approved
- [x] 4.4 复验：几何层 4/4 GREEN 不变；感知层 55%（C5 基线）→24.47%，大幅改善；剩余差距为已确认的架构性差异（真实商品图 vs OD 特定 mockup 图、划线原价/商家分组/推荐区按约定未做）
- [x] 4.5 commit 完成（`c4e86cf`/`5997e88`/`fe492ec` + 复验截图），ledger 已更新

## 5. mp-06 order-confirm

- [x] 5.1 诊断：感知 38.96% RED；几何层 5/5 GREEN（address-card/delivery-card/delivery-opts/summary-card/bottom-bar）。C5 记录的"空购物车直达"问题**已不再出现**（mp-04 购物车数据富化修复后此屏读到的购物车数据也正常了，无需 5.2 的根因排查）
- [x] 5.2 空购物车问题已随 mp-04 修复自动解决，跳过；诊断中新发现更严重的问题：结算金额显示 **"¥404.94000000000005"**——裸浮点数运算精度问题直接漏到用户界面，当前生产环境真实存在
- [x] 5.3 对照 golden 列偏离点：顶部"订单确认"标题栏缺失、地址卡未自动选中默认地址（同 mp-04 cart.js 同类问题，各自独立文件）、"共N件"底部文案缺失；预计送达卡片/"顺丰冷链可达"标签/商家分组"海港直营"/SKU chip 按已定原则跳过
- [x] 5.4 写 task brief（`.superpowers/sdd/mp-od-5-order-confirm-brief.md`），派 implementer 修复（commit `3900051`：优先修浮点数精度 bug（`roundYuan` 四舍五入）、新增顶部标题栏、默认地址自动选中（区分购物车结算/已有订单两分支）、"共N件"真实数据，39 个新测试，434/434 全绿）；task reviewer 用 Node 实测手算复核浮点数修复（确认精确对应线上 bug 数值 404.94000000000005→404.94，舍入方向正确），Approved，0 Critical/Important
- [x] 5.5 复验：几何层 5/5 GREEN 不变；感知层 38.96%→37.76%；金额显示确认已修复（¥404.94）。**新发现范围外问题**：`autoSelectDefaultAddress()` 依赖的 legacy `utils/request.js` 鉴权字段 `app.globalData.token` 只在主动 `login()` 时才桥接，小程序冷启动恢复已登录会话时不会初始化——影响 mp-04/mp-06 两个已上线的默认地址自动选中功能在真实场景下静默失效（不崩溃，优雅降级回手动选择）。已与用户确认记入遗留清单，不在本 change 修复
- [x] 5.6 commit 完成（`3900051` + 复验截图），ledger 已更新

## 6. mp-07 address

- [x] 6.1 诊断：感知 37.9% RED；几何层 2/2 GREEN（address-cards/add-btn）。**C5 记录的 403 问题确认已解决**——`AddressController` 完整实现，3 条种子地址正常加载渲染，无 403
- [x] 6.2 无需排查（403 问题不存在），跳过
- [x] 6.3 对照 golden 列偏离点：顶部"地址管理"标题栏缺失、"设为默认"交互缺失（但发现后端+前端逻辑都已存在，只是 wxml 没接线，不是数据缺口）；"已开通12城冷链"提示条/地址标签"公司""家人"按已定原则跳过；地址详情展示不全是已记录的跨页面已知 bug，不重复处理
- [x] 6.4 写 task brief（`.superpowers/sdd/mp-od-6-address-brief.md`），派 implementer 修复（commit `dcdcddc`：新建 `address-list.json` + 顶部标题栏、接线已有的 `setDefaultAddress` 方法）；implementer 中途发现并（经协调者批准）顺带修复**又一个死绑定真 bug**——`onSelectAddress`/`onEditAddress`/`onDeleteAddress`/`onAddNewAddress` 四处 wxml `bindtap` 引用的方法名在 `address-list.js` 里根本不存在（方向和之前几屏相反：这次是 wxml 引用了不存在的方法），导致整卡选择/编辑/删除/添加新地址**全部**点击无反应，几乎是整个页面核心交互失效。已改绑真实方法名 + 新增 wxml↔JS bindtap 契约测试防回归。441/441 测试全绿；task reviewer 用手动注入回归验证契约测试真实有效（改回错误绑定后测试确实变红），Approved，0 Critical/Important。reviewer 额外发现 2 个非阻塞的既有布局偏离（操作栏竖排 vs OD 横排底部栏；默认地址卡片按 brief 设计不显示"设为默认"行，OD 里默认卡片显示已选中态），记入遗留清单
- [x] 6.5 复验：几何层 2/2 GREEN 不变；感知层遇到 WeChat DevTools 自动化端口跑久了状态异常（screenshot 报 "fail to capture screenshot"，确认是 DevTools 底层问题非代码 bug），重启自动化端口后恢复正常：37.9%→37.55%
- [x] 6.6 commit 完成（`dcdcddc`），ledger 已更新

## 7. mp-08 order-list

- [x] 7.1 诊断：感知 28.98% RED（9 屏里偏离最小之一）；几何层 3/3 GREEN（header/tabs/order-card）。基础结构已比较完整（标题栏/tab/卡片都在）。**本屏起全程用 Monitor + miniprogram-automator `mp.on('console'/'exception')` 事件桥接实时监控 DevTools console**（用户要求，比手动轮询 `mp_getLogs` 更好——查证 miniprogram-automator 内部真有 `App.logAdded`/`App.exceptionThrown` 事件可订阅）
- [x] 7.2 对照 golden 列偏离点：缺"共N件商品"文案；商品缩略图缺失（`OrderItem` 域对象是价格快照，故意不含 imageUrl，架构决策不做）；"剩余支付时间"倒计时（无支付超时业务策略，会是假承诺，不做）；商家名文案（单卖家系统不成立，不做）
- [x] 7.3 写 task brief（`.superpowers/sdd/mp-od-7-order-list-brief.md`），派 implementer 修复"共N件"+样式核对（commit `b446311`）——**过程中发现 `OrderActionRow` 组件（取消订单/去付款/确认收货/申请退款）虽然 JS 逻辑完整且测试覆盖，但 `.wxml`/`.wxss`/`.json` 三个文件完全缺失，从未在任何页面注册，是影响全 app 的核心功能缺失**（spec `mini-program/spec.md:262` 明确要求，用户此前完全没有任何界面能操作订单状态）。已与用户确认现在建（同时服务 mp-08+mp-09，order-detail.json 接线留给 task 8）。implementer 建好展示层后，task reviewer 复查时**又发现两层更深的休眠 bug**：① `order-list.js` 的 `onActionTap` 从 `e.detail` 解构 `{id, action}` 两者都取反了（组件只传 `{id: 动作类型}`，订单 id 在 `dataset.id` 上）② `OrderAPI.js` 运行时 shim 缺 `pay`/`remindShip`/`confirmReceive`/`rebuy`/`requestRefund` 共 5 个方法（`.ts` 源码完整，shim 只同步了一半，`api.test.ts` 一直测 `.ts` 侧测不出来）——组件展示层修好后暴露：点任何订单操作按钮都只会弹"未知操作"，不做真实操作，比"完全不显示"更容易误导用户。已确认属于已授权范围（休眠 bug 被这次改动激活，同类"随屏修真 bug"原则），直接修复（commit `9bfe1e0`）：修正 `onActionTap` 参数读取 + 补全 `OrderAPI.js` 5 个方法 + 新增 `api-shim-contract.test.js`（锁 shim 方法集合，防再次漂移）+ `order-list.js` 此前零覆盖，新增 28 例（TDD 双重 RED→GREEN 验证）。479/479 测试全绿
- [x] 7.4 复验：几何层 3/3 GREEN 不变；感知层 28.98%→29.8%（真实按钮渲染后视觉权重变化，非回归）。**实机端到端验证**：真实 dev-login 注入 token，点击"立即付款"按钮，订单状态**真的从 PENDING 变成 PAID**（`updatedAt` 时间戳同步更新），console 全程干净无报错——证明整条链路（wxml→组件→事件→page handler→OrderAPI→后端→UI 刷新）完全打通
- [x] 7.5 commit 完成（`b446311` + `9bfe1e0`），ledger 已更新

## 8. mp-09 order-detail

> **承接 mp-08 的发现**：`OrderActionRow` 组件文件（`.wxml`/`.wxss`/`.json`）已在 mp-08 建好，这次只需要接进 `order-detail.json` 的 `usingComponents`（不用重新画组件）。**务必核对 `order-detail.js` 如果有自己的 action 点击处理方法，是否正确从 `e.detail.id` 取动作类型、从 `dataset.id`（或页面已有的当前订单 id）取订单 id**——mp-08 的 `order-list.js` 就是在这个事件契约上翻车的（`onActionTap` 从 `e.detail` 解构 `{id, action}` 两者都取反），order-detail 页面走的是单订单详情，可能连订单 id 都不需要从 dataset 取（页面本身就绑定了当前订单），但动作类型一定要从 `e.detail.id` 读，不要重复同样的错误。

- [x] 8.1 诊断：感知 30.32% RED，几何层 3/3 GREEN（status-banner/addr/bottom-bar）。Console 监控全程干净
- [x] 8.2 对照 golden 列偏离点：底部操作按钮硬编码固定 3 个（申请退款/查看物流/确认收货），完全不随订单状态变化（PENDING 订单也显示"确认收货"，真 bug）；物流轨迹硬编码 3 段静态节点，未消费后端真实 `order.tracking.events`；收货人显示裸 `order.userId`（另开 change 处理，不在这次范围）；实时物流追踪数据无后端支撑，不做
- [x] 8.3 写 task brief（`.superpowers/sdd/mp-od-8-order-detail-brief.md`），派 implementer 接线 `OrderActionRow`（mp-08 建好的组件，事件契约 `e.detail.id`，未重蹈 mp-08 一开始"两者都取反"的错误）+ `OrderTrackingTimeline`（早已实现且测试覆盖，只是没被任何页面接线）。**接线过程中同类休眠 bug 第 3 次出现**：`OrderTrackingTimeline/index.js`（mp 运行时真正执行的文件）顶部 `require('./index.ts-helpers')` 指向一个从未存在过的文件——组件此前从未被接线，坏 require 从未被真实执行到，接进 wxml 后 mp 运行时加载会直接崩溃整个组件。修复：内联 JS 版 `computeStages`/`shouldShow`（同 `OrderActionRow/index.js` 既有惯例），新增 `index.runtime.test.js` 直接 require `.js` 运行时文件锁回归。`order-detail.js` 此前零覆盖，新增 38 例测试（commit `fa05537`）。**task reviewer 复查（Approved，0 Critical/Important）**：独立核实坏 require 确实存在、修复逻辑与 `.ts` 源码逐行对比忠实移植、新测试真的锁住 `.js` 运行时文件（不是绕回测 `.ts`）。2 条 Minor：继承自 `order-list.js` 的死分支 `withdrawRefund`（不是本次引入，brief 明确要求照抄模式，合理）+ 测试数字笔误（38 非 39，不影响结论）。范围收敛核实：未碰 `order.userId`，未编造物流数据，未碰后端
- [x] 8.4 复验：几何层 3/3 GREEN 不变；感知层 30.32%→30.24%（残差为已排除范围的预期差异，非新偏差）。**实机端到端验证**：真实 dev-login token → 真实 PENDING 订单 → 点击"立即付款"，后端确认订单真的 PENDING→PAID；订单改 SHIPPED + 真实 tracking events，验证 `OrderTrackingTimeline` 真实渲染 4 节点（证明坏 require 修复在真机有效，不只是单测）。全程 console 监控 0 error/warning/exception
- [x] 8.5 commit 完成（`fa05537`），ledger 已更新

## 9. mp-05 profile

- [x] 9.1 诊断：实测感知 48.74% RED（比 C5 记录的 71% 好，之前几屏的整体样式/token 修正带来正面影响）；几何层 4/4 GREEN（user-card/status-grid[4]/status-cols[4列]/tools-list[3]）。Console 监控全程干净
- [x] 9.2 对照 golden 列偏离点：OD 是丰富会员运营型设计（姓名+VIP等级徽章+积分、收藏/足迹/优惠券三栏统计、积分/优惠券/余额三栏卡片、默认地址详情卡片）；现有实现是极简架构（头像+昵称+角色徽章 + 4状态订单卡 + 3项工具列表）。**已与用户确认**：`User` 域对象无积分/VIP/余额/优惠券/收藏/足迹字段，系统性数据模型缺口（同 mp-02 category 同款问题），只做前端视觉近似，不新增这些板块，记入遗留清单。顺带发现真 bug：7 处图标用系统 emoji（风格不符设计系统）；"联系客服"/"关于我们"两个 `<navigator>` 指向的页面根本不存在也未在 `app.json` 注册，点击必定跳转失败
- [x] 9.3 写 task brief（`.superpowers/sdd/mp-od-9-profile-brief.md`），派 implementer 修复（commit `b109c14`）：用户卡视觉排版微调（字号/间距/圆角，不新增数据内容）；7 处 emoji 全部替换为 `<van-icon>`（复用项目已有的 `@vant/weapp` 依赖，图标名逐一核实真实存在于 vant 图标字体清单）；2 处死链接改为"开发中"toast（同 order-list.js 既有模式）；顺手修复一个此前静默走 fallback 的死 token 引用（`--font-sans`→`--font-body`，前者在 tokens.wxss 里根本不存在）。11 个新测试，528/528 全绿。**task reviewer 复查（Approved，0 Critical/Important，3 条 Minor 均无害）**：范围收敛专项核查确认无任何 VIP/积分/余额/收藏/足迹/默认地址卡片代码混入；7 个 van-icon 图标名逐一 grep 验证真实存在；死链接修复有真实行为断言测试锁定；"收货地址"链接未被误伤
- [x] 9.4 复验：几何层 4/4 GREEN 不变；感知层 48.74%→47.95%（残差为已排除范围的会员运营板块，符合预期，非新偏差）
- [x] 9.5 commit 完成（`b109c14`），ledger 已更新

**新发现（不在本屏范围，已与用户确认现在修，见下方独立处理）**：implementer 诊断中发现登录成功后前端 `authStore` 从未拿到真实用户信息——后端 `TokenResponse`（`POST /api/auth/wechat-login` 响应）只有 token 相关字段，从无 `user` 字段，但 `authStore.js` 的 `login()`/`silentRelogin()` 假设响应体自带 `res.user` 并直接 `persistUser(res.user)`，导致 `userInfo` 恒为 `undefined`——这正是 profile 页面即使已登录也一直显示"点击登录"的根因。调研确认 `GET /api/users/me` 端点已存在，无需改后端契约，纯前端修复：登录成功后补调这个端点。全局性 bug（不止影响 profile 页），已与用户确认现在修。

## 10. 收尾

- [ ] 10.1 全量复跑 `npm run test:visual` + `npm run test:geometry`（无 screen 参数，跑全部 9 屏），确认无回归
- [ ] 10.2 汇总 9 屏最终 diff% / 几何状态表，写入本 change 的完成记录（tasks.md 底部或 design.md 附录）
- [ ] 10.3 检查 `CLAUDE.md`「视觉验证」章节是否需要同步更新（如 9 屏 GREEN 状态、验证方式变化）
- [ ] 10.4 用 `superpowers:requesting-code-review` 走一次全量 diff 的最终 review（既有分散在各屏 commit 的 task review 之外，做一次跨屏一致性检查：token 用法、组件复用、命名风格）
- [ ] 10.5 全部完成后 `/opsx:archive mp-od-prototype-alignment`

## 遗留问题清单（本 change 范围外，供后续 change 参考）

- **`POST /api/orders` 不支持显式 items 建单**（mp-03 诊断时发现，commit `c10c093`）：现在只能从用户服务端购物车建单（`OrderController#create` 无 `@RequestBody`），无法支撑 spec `mini-program/spec.md:91-107` "Direct buy from product detail" 要求的"跳过购物车、items 只含当前商品"完整语义。当前 mp-03「立即购买」是前端近似（先 addItem 合并进购物车再跳订单确认页），未做后端隔离。完全合规需要新增 `POST /api/orders` 的 items 参数支持（或新开一个端点），涉及 DDD 分层改动，建议另开 change。
- **`CartController` 缺 `PUT`/`PATCH /cart/items/:id` 路由**（mp-04 诊断时发现，两轮 reviewer 独立核实）：只有 `GET`/`POST /items`/`DELETE /items/{id}`/`DELETE`。前端 `CartAPI.updateItem()`（数量+/-持久化）/`CartAPI.toggleItem()`（选中态持久化）对应的后端端点根本不存在——数量 +/- 按钮点击目前会打到 404（预先存在的问题，不是这几轮改动引入/加剧的）；勾选态这轮改成纯前端 `reconcileSelection()` 方案绕开（页面实例生命周期内正确，但 reLaunch/小程序被系统回收重建后会丢失、回退成后端默认全选）。完全解决需要给 `CartController` 补这两个端点，建议另开 change。
- **`Address` 领域模型字段名与多处 wxml 引用不一致**（mp-04 复验时发现）：`backend/src/main/java/com/seafood/user/domain/Address.java` 只有 `province/city/detail` 字段，没有 `district`/`detailAddress`。但 `frontend/pages/cart/cart.wxml` 和 `frontend/pages-sub/user/address/address-list.wxml` 都在用 `{{item.district}}{{item.detailAddress}}`——渲染时这两个字段是 undefined，地址详情展示不全（能看到省市，看不到详细地址）。这是跨多个页面的既有 bug，不是这几轮改动引入的，建议另开 change 统一修正字段名对齐（前端改字段名 vs 后端加字段，哪个更合适需要单独判断）。
- **新旧 token 存储没有在小程序冷启动时统一桥接**（mp-06 诊断时发现，影响面可能不小）：legacy `frontend/utils/request.js` 的鉴权请求读 `app.globalData.token`，这个字段只在 `app.js` 旧版 `checkLoginStatus()`（读 `wx.getStorageSync('token')`）或主动调用新版 `authStore.login()`/`loginWithCode()`（内部 `persistUser()` 桥接）时才被赋值。新版登录写的是完全不同的 storage key（`accessToken`，`src/shared/api/storage.ts`），从未写旧 `token` key。结果：小程序冷启动、用户是"之前登录过、这次只是恢复会话"（没有触发新的 `login()` 调用）时，`app.globalData.token` 会保持未初始化，任何仍在用 legacy `utils/request.js` 发鉴权请求的代码都会静默鉴权失败。已确认影响 mp-04 `cart.js` + mp-06 `order-confirm.js` 的默认地址自动选中功能（优雅降级，不崩溃、不脏数据，只是功能不生效）；实际影响面可能更大，取决于还有多少页面仍在用 legacy `utils/request.js` 做鉴权请求，需要单独排查。建议另开 change：在 `app.js` `onLaunch` 时从 `tokenStorage` 恢复 session 时一并初始化 `globalData.token`，彻底桥接新旧两套存储。
- **mp-07 地址卡片操作栏布局是竖排侧边而非 OD 的横排底部栏**（mp-07 reviewer 发现，既有布局，本轮未触碰）：`address-list.wxss` 的 `.address-card__actions` 是 `flex-direction: column` + 右侧 `border-left` 竖排（编辑/删除/设为默认纵向堆叠），OD 图是卡片底部一条横向操作栏（虚线分隔）。持续拖累这一屏的感知 diff 分数，不是本次改动引入，brief 范围未要求调整，留给后续视觉打磨 change 处理。
- **`User` 域对象无积分/VIP等级/余额/优惠券/收藏/浏览足迹字段**（mp-05 profile 诊断时发现）：OD 原型个人中心页展示丰富的会员运营板块，现有 `UserDocument` 只有 `id/openId/nickname/avatarUrl/role/phone/addresses/createdAt`。已与用户确认本 change 只做前端视觉近似（保留极简架构），不新增这些板块。完整对齐需要新的领域能力（积分累计规则、VIP 等级判定、余额账本、优惠券发放与核销、收藏/足迹追踪），涉及新聚合根设计，建议另开 change 单独设计。
- **（已修复，非遗留）登录成功后 `authStore.state.user` 恒为空**：mp-05 诊断阶段发现的全局性 bug（后端 `TokenResponse` 从无 `user` 字段，前端却假设有），已确认现在修，不留作遗留问题——commit `d9951fb`（mp 运行时 `store.js` + 新建 `user/api.js` shim）+ `a0a60f1`（task reviewer 指出 `store.ts` 类型检查源码未同步这次修复，属于本 change 反复出现的"shim drift"反向重演，已一并同步修复，536/536 全绿，`tsc --noEmit` 0 error）。
