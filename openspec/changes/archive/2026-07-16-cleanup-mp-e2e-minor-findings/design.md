## Context

本 change 打包两个独立的 Minor 发现,互不依赖,合并成一个 change 避免散落遗忘。

**1. `goToDetail` 死代码**

`frontend/pages/index/index.js:234` 与 `frontend/pages/category/category.js:133` 都定义了
`goToDetail(e)`,内含登录守卫:

```js
goToDetail: function (e) {
  const app = getApp();
  if (!app.globalData.userInfo) {
    wx.navigateTo({ url: '/pages-sub/user/login/login' });
    return;
  }
  const id = e.currentTarget.dataset.id;
  wx.navigateTo({ url: `/pages-sub/product/product-detail/product-detail?id=${id}` });
},
```

但两个页面的 wxml(`index.wxml:172`、`category.wxml:81`)商品卡片全部用 `<navigator url="...">` 直跳,从未 `bindtap="goToDetail"`。经全文 grep 确认两个 wxml 里不存在任何 `goToDetail` 绑定点——这个守卫从写下的第一天起就没被执行过。

`product-detail.js` 的 `onLoad`(第 48 行)本身**没有**登录检查,只有 `onBuyNow`(立即购买)才检查 `userInfo`——证实"详情页可公开浏览"本来就是当前真实、且唯一被验证过的行为,`goToDetail` 里的守卫描述的是一个从未生效过的意图。

更进一步:`index.test.js:260-267` 与 `category.test.js:155-162` 各有 2 条专测 `goToDetail`(已登录跳详情 / 未登录跳登录页)的用例——这些测试全绿,但测的是一段永远不会被真实用户路径触发的代码,是"看起来有覆盖率,实际零验证价值"的典型案例。

**2. seed 订单 fixture 孤儿化,且比 proposal 描述的更深一层**

`backend/seed/fixtures/orders.json` 唯一的订单 `userId: "dev-user-001"`,`users.json` 里没有任何 `_id` 或 `openId` 等于这个值(两个 seed 用户的 openId 分别是 `admin-seed-001`/`customer-seed-001`,`_id` 未显式指定,由 MongoDB 插入时自动生成)。`mp` 按登录用户的 `userId` 过滤订单(`OrderRepository.findByUserId`),这条订单对任何真实登录用户都不可见。

**追加发现(proposal 未预料到)**:即便把 `orders.json` 的 `userId` 改成对齐 `customer-seed-001` 用户的真实 `_id`,验收场景("the seeded customer user can authenticate and see the seeded order")依然无法达成——因为 `customer-seed-001` 这个 openId 本身在开发模式(`wechat.enabled=false`)下**不可登录**:

- `WechatCodeExchanger.exchange(code)`:`wechat.enabled=false` 时只接受 `code` 以 `"dev-"` 开头,且**直接把整个 `code` 字符串(含 `dev-` 前缀)当作 openId 返回**(`WechatCodeExchanger.java:33-34`)
- `AuthService.wechatLogin`:用这个 openId 做 `findByOpenId(...).orElseGet(创建新用户)`(`AuthService.java:120-128`)
- 所以要在开发模式下登录到 openId 恰好等于 `customer-seed-001` 的账号,必须发送 `code="customer-seed-001"`——但这个 code 不以 `dev-` 开头,`exchange()` 直接 `throw new DomainException(...)`,登录请求本身就会被拒绝
- 全仓搜索确认:`customer-seed-001` 目前只出现在本 change 自己的 planning artifact 里,没有任何脚本/E2E harness 依赖这个具体字符串——它是本 change 引入的新验收目标,不是需要向后兼容的既有契约

memory `c5-visual-test-runbook.md` 记录过相关但**不同**的问题:活体 mp E2E harness(`frontend/e2e/tools/mp-harness.cjs`)用固定 `DEV_CODE='dev-visual-001'` 登录,拿到运行时真实分配的 `userId` 后再调用 `seedOrdersFor(userId)` 动态补单——这是因为 `openId` 无唯一索引,`_id` 在反复 reseed/relogin 间会漂移,harness 选择"登录后再补数据"规避漂移,而不是"预先造一个假定固定 `_id` 的用户"。这个 harness 从未尝试登录到 `customer-seed-001`,所以它没暴露上面这个 openId 前缀问题;`seed.sh` 是一次性静态脚本(执行期间不发生登录),不与 harness 共享运行时,不能直接复用 `seedOrdersFor`,但会复用同一个底层教训:**不要在种子数据里假定一个还没验证过能被真实登录路径命中的身份标识**。

## Goals / Non-Goals

**Goals:**
- 删除 `goToDetail` 死代码守卫 + 两个专测死代码的测试用例,消除误导,零行为变化
- `seed.sh` 跑完后,种子订单能被种子 customer 用户在**真实开发模式登录**后通过 `GET /api/orders` 看到(不是仅在 MongoDB 里字段对得上,而是端到端可达)
- 让 `orders.json` 的 `userId` 不再是写死猜测值,而是运行时对齐真实插入的 `_id`

**Non-Goals:**
- 不修 `openId` 缺唯一索引这个更深的结构性问题(会导致反复 reseed/relogin 场景下 `_id` 漂移)——这是活体 E2E harness 已经用 `seedOrdersFor(运行时 userId)` 规避掉的问题,超出"让一次性 seed.sh 产出自洽数据"这个本次范围;留 Open Question
- 不改 `product-detail` 页面本身的鉴权语义(`onBuyNow` 的登录检查不动)
- 不处理"详情页需要登录"这个产品需求变更(proposal 里的备选路线,需要产品拍板,本 change 默认取"维持公开"这条零行为变化路线)

## Decisions

**1. `goToDetail` 与其死代码测试一并删除,不保留 TODO 占位**

两个页面各删 `goToDetail` 方法体(`index.js`/`category.js`)+ 各自文件里专测它的 2 条用例(`index.test.js:260-267`、`category.test.js:155-162`)。不选"保留函数体 + 加 TODO 注释"这条 proposal 里提到的备选项——那是给"详情页需要登录"这个尚未拍板的产品决策预留的分支,当前默认路线(维持公开)下保留一段已确认永远不执行的代码只会继续误导下一个读者,删除才是这条路线该有的终态。

**2. `seed.sh` 里先插入 users,查询回真实 `_id`,再用它 patch orders.json 后导入**

而不是给 `users.json` 的 customer 条目手写一个固定 `_id` 字面量(表面上更简单,`insertOne` 会保留调用方提供的 `_id`)。理由:

- 手写固定 `_id` 只在"这次 seed.sh 是数据库里第一次出现这个 openId"时才可靠;一旦种子数据被拿去和会做多轮 relogin 的活体 harness 混用(未来完全可能),固定 `_id` 会和 memory 记录的"`_id` 漂移"问题撞在一起,产生新的隐性假设
- 动态查询回真实 `_id` 不依赖"这是不是第一次"这个前提,天然对齐 harness 已验证过的"永远用运行时真实值,不猜"这条经验教训
- 实现形状(shell 层,交给实现阶段落地,design 只定方向):`users.json` 导入循环结束后,`mongosh --eval "db.users.findOne({openId:'<customer openId>'})._id"` 取出真实 `_id`;导入 `orders.json` 前用这个值 patch 每条订单的 `userId` 字段(例如 `jq --arg uid "$CUSTOMER_ID" 'map(.userId = $uid)'`),再插入 patch 后的文档,而不是原始 fixture 文件

**3.(追加决策,补 proposal/tasks.md 未预料到的缺口)customer 种子用户的 `openId` 改成 `dev-` 前缀**

`users.json` 里 customer 条目的 `openId` 从 `customer-seed-001` 改成 `dev-customer-seed-001`(或等价的 `dev-` 前缀值)。不改这一步,决策 2 做得再对,验收场景("种子 customer 登录后能看到种子订单")依然不可达——因为开发模式登录要求 `code` 以 `dev-` 开头,且 `openId` 就是那个完整 `code` 字符串,`customer-seed-001` 本身永远进不去开发模式登录路径。改名后:测试/开发者用 `code="dev-customer-seed-001"` 登录 → `openId="dev-customer-seed-001"` → `findByOpenId` 命中种子阶段已插入的这条用户文档 → 复用其真实 `_id` → 该 `_id` 正是决策 2 里用来 patch 订单 `userId` 的同一个值,闭环。

这一条不在 tasks.md 当前的任务范围内(task 2.1 目前只提到改 `seed.sh` + `orders.json`,没提 `users.json` 的 `openId`)——实现阶段需要把 `users.json` 也纳入 task 2.1 的改动文件列表,详见 Open Questions。

## Risks / Trade-offs

- [risk] `seed.sh` 里用 `mongosh --eval` 查询回 `_id` 再喂给 `jq` 属于新增的一次跨进程数据传递,若 `mongosh --eval "print(...)"` 的输出格式包含额外空白/换行会污染 patch 后的 `userId` 字符串 → [mitigation] 实现时用 `tr -d '[:space:]'` 或等价方式清理输出,task 2.2 的验收步骤(seed 后真实调 `GET /api/orders` 断言可见)会直接暴露这类格式问题,不会静默通过
- [risk] `openId` 无唯一索引(memory 已记录)意味着理论上还是可能出现同一 openId 的重复用户文档,决策 3 只保证"新鲜 `seed.sh` 跑完后紧接着第一次登录"这个场景可靠,不保证长时间混用/反复 reseed 后依然可靠 → [mitigation] 明确写进 Non-Goals,不在本 change 里加索引;真要根治留给独立的后续 change
- [risk] 删除 `goToDetail` 死代码测试会让这两个文件的用例数各减少 2 条,如果有人用"用例总数"当覆盖率代理指标会看到数字下降 → [mitigation] 这是移除无效覆盖,不是移除有效覆盖,`npm test` 的语句/分支覆盖率不会因此下降(task 1.3 已经把这一步纳入验收)

## Migration Plan

- 纯代码 + fixture 改动,无生产数据迁移(`orders.json`/`users.json` 只在 dev/CI 种子库里生效,不触生产 MongoDB)
- 部署无特殊顺序要求
- 回滚:`goToDetail` 走 git revert 即可;seed 脚本回滚后 `orders.json` 重新变回孤儿状态,不影响任何已有真实业务数据(seed 数据本身就是可重建的)

## Open Questions

- tasks.md task 2.1 当前只写"改 `seed.sh` + `orders.json`",需要在实现前追加"同步把 `users.json` 里 customer 条目的 `openId` 改成 `dev-` 前缀值"这一步(决策 3),否则验收场景(spec.md 的 Scenario: Seed script produces a visible order for the seeded customer)会卡在"登录不进去",不是"订单看不到"这一层
- 决策 3 改了 `openId` 字面量,如果将来有其它脚本/文档假设过 `customer-seed-001` 这个具体字符串(本次全仓搜索未发现),需要同步更新——现状确认无此类依赖,风险低
