## Context

`pages-sub/user/address/address-edit.js` 的 `validateForm` 校验 `formData.province`,但地区选择用的是微信原生 `picker mode="region"`,表单字段名是 `region`,`bindsubmit` 触发时 `e.detail.value` 只产出 `region: [province, city, district]` 三元素数组,**从不产出顶层 `province` 字段**。结果 `formData.province` 恒为 `undefined`,`validateForm` 里 `!formData.province` 恒真,不论用户是否真的选完省市区,「请选择所在地区」错误恒报——2026-07-13 零 mock E2E 实测到的现象:全字段填满仍无法保存地址,阻断整条下单链路(P0)。

`saveAddress` 中派生 `province/city/district` 的逻辑(`formData.region[0..2]`)本身是对的——bug 完全局限在 `validateForm` 这一处,校验字段名和表单实际产出字段名对不上。

## Goals / Non-Goals

**Goals:**
- `validateForm` 校验的字段与表单 `bindsubmit` 实际产出的字段(`e.detail.value`)保持一致,消灭这类"校验函数猜字段名、和 wxml 实际不符"的契约漂移
- 用户填完姓名/手机号/省市区/详细地址后,能够真正保存成功(RED→GREEN 用真实 formData 形状锁死)
- 补齐/修正 Jest 用例,把此前用虚构 `province: '北京'` 字符串(从不存在的表单产出形态)造数据的反模式一并清掉,避免未来测试继续掩盖真实 bug

**Non-Goals:**
- 不改 `POST /api/addresses` / `PUT /api/addresses/{id}` 后端契约(E2E 已证实后端链路本身可用)
- 不改 wxml 地区选择控件的产出方式(不新增隐藏 `name="province"` 字段去凑 `validateForm`,那是给症状打补丁而非修正校验本身)
- 不改 `saveAddress` 的省市区派生逻辑(已验证正确,不在本次改动范围)
- 不做地区选择器的交互/布局改动

## Decisions

**1. 修 `validateForm` 去匹配表单实际产出,而不是让表单去凑校验**

两个可选方向:
- (A) 把 `validateForm` 校验目标从 `formData.province` 改成 `formData.region`(数组存在、长度为 3、且第一项不是默认占位符 `'请选择'`)
- (B) 在 wxml 里给 picker 额外挂一个隐藏的 `name="province"` 字段,凑出 `validateForm` 想要的形状

选 (A)。理由:
- `saveAddress` 已经在正确地从 `formData.region[0..2]` 派生 province/city/district,说明 `region` 数组才是这个表单唯一的、被下游正确消费的地区数据来源;(B) 会制造第二份冗余数据源,两处字段将来又可能再次漂移
- 校验逻辑的职责是"校验表单实际产出什么",不是"表单产出什么去凑校验想要什么"——(A) 是修正因果关系倒置的根因,(B) 只是掩盖症状

**2. 占位符检测只查 `region[0]`,不逐一比对三项**

微信 `picker mode="region"` 是一体选择控件:用户要么没碰过(三项都是初始 `['请选择','请选择','请选择']`),要么一次选择产出完整三元组,不存在"只选了省、市区还是占位符"的中间态(UI 层面不允许)。因此只判 `region[0] === '请选择'` 即可覆盖"未选择"这一真实场景,逐一比对三项是不会被触发的死代码。`region.length < 3` 的兜底判断保留,防御 formData 结构异常(理论上不应发生,但比崩溃在后续派生逻辑里更安全)。

**3. 测试策略:锁定真实 formData 形状,而非抽象契约断言**

proposal 里最初设想是加一条"表单 submit 产出的字段集 ⊆ validateForm 校验的字段集"的通用契约测试。实现时发现更直接有效的做法是:直接用真实的 `e.detail.value` 形状(`region` 数组,无 `province` 顶层字段)构造测试用例喂给 `validateForm`,RED 阶段真实复现了 bug(province 恒 undefined 导致误报)。同时顺手修正了同文件里 6 个已有测试——它们此前用手造的 `province: '北京'` 字符串在测,这本身就是一种从不存在于真实表单里的数据形状,是测试没抓住 bug 的根本原因。抽象契约测试留作可能的后续加固,不是本次修复的阻塞项。

## Risks / Trade-offs

- [risk] `validateForm` 修复只覆盖了本次 E2E 实测到的字段名不匹配,不能排除表单未来新增字段时校验遗漏 → [mitigation] 已修正的 6 个既有测试 + 新增用例覆盖了 `region` 数组的完整/占位符/长度不足三种输入形态,后续新增字段应遵循"先看 `e.detail.value` 真实形状再写校验"的同一原则(已在 `validateForm` 内联注释里记录教训)
- [risk] 视觉/布局层面本次改动为零,但未跑 `test:visual`/`test:geometry` 走一遍确认(tasks.md 4.2 未勾选)→ [mitigation] 改动仅限 JS 校验逻辑,不触 wxml/wxss,预期风险低;留作 Open Question,archive 前决定是否补跑

## Migration Plan

- 纯前端 JS 改动,无数据/接口迁移,无 feature flag
- 小程序版本发布后立即生效,不需要特殊上线顺序
- 回滚:还原 `address-edit.js` 的 `validateForm` 方法即可,无残留状态

## Open Questions

- tasks.md 4.2(`npm run test:visual` / `npm run test:geometry`)是否需要在 archive 前补跑?本次改动未触布局,预期风险低,但尚未有实测证据排除意外——建议 archive 前显式决定"跳过并记录理由"或"补跑一次"
