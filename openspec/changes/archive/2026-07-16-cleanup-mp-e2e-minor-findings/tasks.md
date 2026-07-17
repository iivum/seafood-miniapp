## 1. goToDetail 死代码清理(零行为变化,默认路线:详情页保持公开)

- [x] 1.1 确认 `frontend/pages/index/index.wxml`/`frontend/pages/category/category.wxml` 的商品卡片确实全部经 `<navigator>` 直跳,未绑定 `goToDetail`(2026-07-13 E2E 已初步核实,复核一遍避免遗漏其他绑定点)——复核完毕:两个 wxml 里所有 bindtap/catchtap 逐条列出,均无 `goToDetail`
- [x] 1.2 若产品未来决定"详情页需登录"(需求变更,超出本 change 范围),保留 `goToDetail` 函数体但补一条 TODO 注释说明当前未接线;若维持现状(默认),直接删除 `frontend/pages/index/index.js` 与 `frontend/pages/category/category.js` 中未使用的 `goToDetail` 方法——取默认路线,已删除两处方法体
- [x] 1.3 `cd frontend && npm test` 确认删除后无用例依赖该死方法(若有测试专测 goToDetail,一并评估是否该测试本身也是无效覆盖)——确认是无效覆盖,连带删除 `index.test.js`/`category.test.js` 里专测 goToDetail 的 4 条用例;`pages/index`+`pages/category` 目录 50/50 pass(54→50,减少数与删除用例数一致),覆盖率无下降

## 2. seed 订单 fixture 归属修复

- [x] 2.1a(design.md 决策 3,实现前必做,否则 2.2 无法通过)`backend/seed/fixtures/users.json`:customer 条目的 `openId` 从 `customer-seed-001` 改成 `dev-customer-seed-001`(或等价 `dev-` 前缀值)——开发模式登录要求 code 以 `dev-` 开头且直接把整个 code 当 openId 用,不带 `dev-` 前缀的 openId 在开发模式下永远登录不进去,与 2.2 的验收目标无关但是其前提
- [x] 2.1 `backend/seed/seed.sh`:导入 `users.json` 后先查询回 customer 用户的真实 `_id`(`db.users.findOne({role:'CUSTOMER'})._id.toString()`,`tr -d '[:space:]'` 清理输出空白),导入 `orders.json` 前用 `jq --arg uid ... 'map(.userId = $uid)'` 动态 patch 每条订单的 `userId` 字段(而非写死 `dev-user-001`)
- [x] 2.2 新增 `backend/scripts/seed-order-visibility-smoke.sh`(shell 层验收,零 mock):跑一次 seed 后,用 `code="dev-customer-seed-001"` 走 `/api/auth/wechat-login` 换真实 JWT,再调 `GET /api/orders` 断言能看到该订单。TDD 走完整 RED→GREEN:修复前跑该脚本先实测 RED(登录成功但 `GET /api/orders` 返回 0 条,失败原因正确——不是脚本写错,是订单确实孤儿化);实现 2.1a+2.1 后重跑转 GREEN(返回 1 条订单)。**顺带发现并绕过一个无关的 pre-existing bug**:`seed.sh` 步骤 2(导入 `categories.json`)对 MongoDB 报 `Performing an update on the path '_id' would modify the immutable field '_id'`,导致 `set -euo pipefail` 下整个脚本从未跑完过第 2 步——本次验证时临时移出 `categories.json` 绕过(跑完后原样恢复,`git status` 确认无残留),**未修复**,超出本 change 范围,已建议另开 change 处理
- [x] 2.3 更新 memory `c5-visual-test-runbook.md`(seed.sh deleteMany 段落追加本次修复状态)/ `mp-e2e-fullstack-2026-07-13.md`(状态表 5→✅完整 + 新增详细实现记录小节 + 环境复用要点补 openId 变更提示 + 第 3 次撞见 categories.json bug 记录)

## 3. 回归

- [x] 3.1 `cd frontend && npm test` 全量通过——80 个 suite / 749 例全过(753→749,减少数与 task 1.3 删除的 4 条死代码用例数一致),0 失败
- [x] 3.2 重跑一次本地 seed(`bash backend/seed/seed.sh`,跳过既存 categories.json bug,见 2.2 记录),确认 products(50)/users(2)/banners(3)/orders(1)计数与此前一致;`seed-order-visibility-smoke.sh` 复跑仍 GREEN
