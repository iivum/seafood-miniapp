## 1. 测试先行(TDD)

- [x] 1.1 在 `frontend/pages-sub/user/address/__tests__/address-edit.test.js` 补失败用例:`validateForm` 传入「region 数组完整、无 province 字段」的真实 formData 形态,断言不报"请选择所在地区"(RED 已实测确认:失败原因正是 bug 本身——province 恒 undefined)
- [x] 1.2 补用例:`validateForm` 传入「region 为默认占位 `['请选择','请选择','请选择']`」,断言报"请选择所在地区"
- [x] 1.3 补用例:`validateForm` 传入「region 缺失/长度不足 3」,断言报"请选择所在地区"(容错未选情形)
- [x] 1.3a 顺带修正同文件里 6 个已有测试:原用手造 `province: '北京'` 字符串(从不存在的真实契约,同类反模式见文件头 request mock 注释),改为真实 `region` 数组形状

## 2. 修复实现

- [x] 2.1 `address-edit.js` 的 `validateForm` 改为校验 `formData.region`(数组存在、长度为 3、且不等于默认占位值),移除对不存在的 `formData.province` 的校验
- [x] 2.2 确认 `saveAddress` 中 `province/city/district` 派生自 `formData.region[0..2]` 的逻辑保持不变(已正确,仅 validateForm 有 bug)
- [x] 2.3 跑 1.1-1.3 用例转绿(17/17 pass,`pages-sub/user/address/` 全目录 40/40 pass)

## 3. E2E 验收(零 mock)

- [x] 3.1 mp 运行时内真实走「进入地址新增页 → 填姓名/手机号/选择省市区/填详细地址 → 保存」,断言地址列表出现新地址(接真后端 `POST /addresses`,真 MongoDB)——2026-07-14 mp-e2e-expert PASS,地址真实落库 `users.addresses`,列表页真实渲染
- [x] 3.2 复测 2026-07-13 E2E 报告里被阻断的「加购 → 结算页选择/创建地址 → 提交订单」完整链路,确认地址环节不再阻断——PASS,`carts`/`orders` 均真实落库,订单引用的地址 id 与新建地址一致
- [x] 3.3 更新/清理 memory `mp-e2e-fullstack-2026-07-13.md` 中本条 bug 的状态

## 4. 回归

- [x] 4.1 `cd frontend && npm test` 全量通过——**753 例中 1 例失败**(`utils/__tests__/order-detail-derive.test.js`),已用 `git stash` 验证该失败在改动前就存在、与本次改动无关(时间敏感 flaky 测试,详见 memory `frontend-flaky-order-detail-derive-time`),不阻塞本 change;`pages-sub/user/address/` 全目录 40/40 pass,无覆盖率下降
- [x] 4.2 评估结论:`address-edit` 不在 C5 视觉/几何 golden 集合内(`frontend/e2e/tools/visual-diff.cjs` 的 `mp-07-address` 映射到 `address-list` 列表页,不含编辑页),`npm run test:visual`/`npm run test:geometry` 测不到本次改动触及的页面,对本次改动无额外验证价值,跳过
