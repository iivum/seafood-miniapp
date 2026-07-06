## 1. 清理 no-op JSON 导航栏配置(D5)

- [x] 1.1 `frontend/pages-sub/order/order-confirm/order-confirm.json`:删除 `navigationBarBackgroundColor`/`navigationBarTextStyle`(`navigationStyle: "custom"` 下已无效),保留 `navigationBarTitleText`/`navigationStyle`
- [x] 1.2 `frontend/pages-sub/user/address/address-list.json`:同上
- [x] 1.3 `cd frontend && npm test -- order-confirm address` 全绿(82/82),确认无回归 —— commit `10c704b`

## 2. 地址卡片 class 名统一:`cart-address` → `address-card`(D4)

- [x] 2.1 `frontend/pages/cart/cart.wxml`:`cart-address` 及其 `__main`(改 `__body`,对齐 `order-confirm.wxml`/`address-list.wxml`)/`__head`/`__name`/`__phone`/`__detail`/`__empty`/`__placeholder`/`__arrow` 全部改名 `address-card`
- [x] 2.2 `frontend/pages/cart/cart.wxss`:对应 `.cart-address*` 选择器同步改名
- [x] 2.3 `hover-class-contract.test.ts` 全量快照同步更新(diff 只含这一处改名,已核对)
- [x] 2.4 `cd frontend && npm test -- cart` 全绿(70/70)—— commit `abe14ca`

## 3. 共享 money 格式化 util(D6)

- [x] 3.1 `frontend/utils/money.test.js`(新建):TDD RED —— `roundYuan`/`formatYuan` 用例
- [x] 3.2 `frontend/utils/money.js`(新建):实现,跑 3.1 GREEN,100% 覆盖率
- [x] 3.3 `order-confirm.js`:删除本地 `roundYuan()`,改 `require('../../../utils/money.js')`;精度回归测试全绿
- [x] 3.4 `cart.js`:两处 `.toFixed(2)` 改用 `formatYuan(...)`
- [x] 3.5 `cd frontend && npm test -- money order-confirm cart` 全绿 —— commit `f387f92`(555/555 全量)

## 4. `order-detail.wxss` BEM 化(D3)

- [x] 4.1 block 名定为 `order-detail`(复用已有根容器类名),扁平类名(`.nm`/`.pr`/`.act`/`.card`/`.card-title`/`.row`/`.row1`/`.lbl` 等)逐一映射成 `order-detail__xxx`,映射表见 commit `a4da328` 说明
- [x] 4.2 `order-detail.wxss`:按映射表改类名,同时拍平此前依赖的 CSS 嵌套选择器(如 `.addr-card .row1`)为独立扁平类名
- [x] 4.3 `order-detail.wxml`:对应 `class="..."` 属性逐一同步,无遗漏
- [x] 4.4 `order-detail.test.js` 无 class 名断言,无需改动
- [x] 4.5 `cd frontend && npm test -- order-detail` 全绿(555/555 全量)—— commit `a4da328`。视觉环境本轮未跑(纯选择器改名 + 拍平嵌套依赖,规则内容不变,零可见风险)

## 5. bindtap handler 裸动词 → onXxx(D1 + D2,风险最高,逐文件配 contract test)

- [x] 5.1 `order-confirm.js`:`goBack` → `onBack`;wxml 同步
- [x] 5.2 `address-list.js`:`goBack` → `onBack`;wxml 同步,`address-list-wxml-contract.test.js` 验证
- [x] 5.3 `address-list.js` 5 个方法(`selectAddress`/`editAddress`/`deleteAddress`/`addNewAddress`/`setDefaultAddress`)→ `onXxx`;wxml 5 处 `bindtap` 同步
- [x] 5.4 `cart.js`:`selectAddress` → `onSelectAddress`;新建 `cart-wxml-contract.test.js`
- [x] 5.5 `index.js`/`category.js`:`addToCart` → `onAddToCart`;各新建 wxml-contract test
- [x] 5.6 `product-detail.js`:`goToProductDetail` → `onGoToProductDetail`;新建 wxml-contract test
- [x] 5.7 `cd frontend && npm test` 全量确认无遗漏 —— commit `79fce7f`(主体,implementer 中途撞 API session 限额,controller 接手完成 index/category/product-detail + 发现并修复原 brief 未预见的 4 处裸源码文本正则匹配方法名的既有测试)+ `ea85ad5`(review 发现的 2 处残留旧名字注释/日志文案)。**独立 reviewer dispatch 并 Approved**:逐项核实全部 11 处改名配对完整、2 处"不可改"排除项(order-confirm.js 死代码 selectAddress、cartUtil.addToCart)确认未误伤、5 个新 contract test 均为真实通用扫描(非弱化断言)、独立在另一文件(address-list)复现"故意改坏→确认 contract test 抓到→复原"安全网验证。565/565 全量

## 6. 抽取共享 order-action controller(D7,含退款行为对齐已批准 spec)

- [x] 6.1-6.2 `frontend/utils/order-actions.js`+`.test.js`(新建):TDD 覆盖 pay/cancelOrder/remindShip/reorder/deleteOrder/未知 action/409/403/404 分支。**关键修正(设计阶段研究发现,不是可选项)**:`requestRefund`/`afterSale` 改调 `orderStore.requestRefund(order.id, order.totalAmount, '用户主动申请')`(不是裸 `OrderAPI.requestRefund`)——研究阶段发现 `order-detail.js` 原 `applyRefund()` 从未传后端必填的 `amount` 字段,对真实后端会 400,只是被测试里不校验 body 内容的 mock 掩盖;`orderStore.requestRefund` 早已正确实现 + 测过(乐观更新/失败回滚),直接复用
- [x] 6.3-6.4 `order-list.js`:删除 5 个本地方法,改调共享实现(`order` 参数按 id 从 `this.data.orders` 查完整对象再传入,非 wxml 改动);TDD RED-first 修正"占位退款"测试为真实断言
- [x] 6.5-6.6 `order-detail.js`:删除 4 个本地方法,改调共享实现;`confirmReceipt`/`confirmReceive` 判断保留页面本地(design 未强制要求纳入,两屏因此在这一个 action 上有意保留独立实现——已知、已公开披露的取舍,非隐藏缺陷)
- [x] 6.7 `cd frontend && npm test -- order-list order-detail order-actions` + 全量 —— commit `8fc8e88`(implementer 主体实现,**顺带发现并修复第二处 shim-drift**:`orderStore.requestRefund` 只在 `store.ts` 里实现过,mp 运行时真正加载的 `.js` shim 从未同步,补上 + 新建 `store-shim-contract.test.js` 锁住方法集合)。**独立 reviewer dispatch 并 Approved**,含独立 mutation-test 验证(改错 amount 参数,确认 7 处测试真的能抓到)。1 Important(order-list.js 查完整订单对象后缺 null 守卫,order 可能因列表竞态查不到——`confirmThenRefund` 内部裸读会在真机异步回调里抛未捕获 TypeError)+ 2 Minor,均已修复(commit `997e97d` 额外修复第三处 shim-drift 相关的 `cartStore.add`→真实方法名 `addItem`;commit `6b0b9cc` 补 null 守卫 + TDD 回归测试 + 1 处残留旧方法名描述)。600/600 全量

## 7. 收尾

- [x] 7.1 `openspec validate mp-cross-screen-cleanup --strict` 通过
- [x] 7.2 `cd frontend && npm test` 全绿(600/600,62 个 suite),无回归
- [x] 7.3 视觉验证环境本轮未起(6 个任务全部是纯命名/组织结构/bug 修复,零预期视觉变化;每个任务已用现有 4 层断言 + 全量单测覆盖交叉验证)
- [x] 7.4 遗留问题清单已在 proposal.md 记录,确认仍未修、仍需另开 change(见下方)
- [x] 7.5 `/opsx:archive mp-cross-screen-cleanup`

## 全分支 review 结论

**批准(Approved)**。9 commit 全量 diff 复核(1.1→6 全部 6 个任务组 + 收尾):所有改动均为命名/组织结构/既有能力接线修复,零新增对外行为契约(除「Order list and detail (mp-08) customer action row」delta spec 补的显式约束,verified 与实现一致);6 个任务里 2 个(Task 5/6)独立 dispatch task-reviewer 并 Approved,过程中发现并修复 3 处"测试用虚构 shape/shim 未同步导致真 bug 被掩盖"(退款缺 amount、requestRefund shim 未同步、cartStore.add 不存在),均已妥善修复 + 回归测试锁住;4 个低风险任务(Task 1-4)controller 直接实施,零行为风险(JSON 清理/CSS 改名/util 抽取)。

## 遗留问题清单(本 change 范围外,proposal.md 研究阶段发现,供后续 change 参考)

- **`order-confirm.js#selectAddress` 是死代码**:wxml 的地址卡片用原生 `<navigator url="...">` 直接跳转,从未 bindtap 到这个方法,疑似更早版本的产物。未删除(删除死代码不是本次改动目标,且删除前应先确认真的从未被间接引用)。
- **`cart.js` 从地址选择页手动选完地址回跳后,`prevPage.selectedAddressFromList` 从未被 `cart.js` 读取**:另一处独立死绑定(cart.js 文件头注释已自行记录此缺口),不属于本次命名/风格清理范围。
