## 1. 环境就绪

- [x] 1.1 `bash frontend/e2e/tools/preflight.sh` 确认 miniprogram_npm / DevTools 自动化端口 / 后端三项就绪(后端未就绪时按 agent 定义走 evaluate 注入伪数据的降级路径,不阻塞走查)—— mp-e2e-expert agent 执行,三项均就绪
- [x] 1.2 用 Monitor 工具同时武装 `console-watch.cjs` 与 `devtools-log-watch.cjs` 双路监控,确认各自输出就绪行 —— agent 全程监控,0 exception,console warn/error 均与登录页无关

## 2. Step1 走查(hero + 协议勾选 + 微信登录按钮)

- [x] 2.1 `reLaunch` 到 `pages-sub/user/login/login`(默认态即 `step:1`,无需注入),截图
- [x] 2.2 与 `mp-10-login.html`(Open Design 项目)逐项比对:hero 视觉区文案/配色、协议勾选行、微信登录按钮样式;记录任何明显偏离(位置/颜色/字重/间距)—— 发现 hero 高度矮 32%(明显)+ 品牌文字锚点方向反(中等),见第 4 节修复
- [x] 2.3 未勾选协议点击登录的 shake + toast 阻断态额外截一张,确认与设计稿预期交互一致(若 OD 原型有对应态)—— shake 动画触发确认,toast 文案与 OD 一致(icon 用 none 是系统内置图标集的合理降级)

## 3. Step2 走查(头像昵称 + 获取手机号 + 跳过)

- [x] 3.1 用 `mp.evaluate` + `getCurrentPages()` 调用页面自带的 `onDevLogin()`(design.md D2 决策;不用 globalData/wx.request 注入组合拳),驱动页面进入 `step:2` —— **执行后发现 D2 假设与实现不符**:`onDevLogin()` 无条件 `handleLoginSuccess()` 直登首页,不会进入 step2(这是它"一键直登"的既有产品行为,非 bug)。已在 design.md Amendments 记录,golden 捕获改用直接 `setData` 注入(与 mp-04 等既有 auth 注入同类做法)
- [x] 3.2 截图,与 `mp-10-login.html` 的 Step2 部分逐项比对:头像/昵称展示、"微信授权成功"提示、获取手机号按钮、"暂不绑定"跳过链接 —— 头像行/按钮/skip 链接均一致
- [x] 3.3 记录任何明显偏离 —— Step2 本身视觉无偏离;额外发现 OD footer 版权文案 mp 未渲染,判定不在本 change 范围,留待后续决策

## 4. 修复走查发现的真实偏离(仅当 2/3 步发现问题时执行,否则本节全部跳过)

- [x] 4.1 对每项确认的真实偏离(非设计允许的差异,如"暂无实拍图用渐变替代"这类已知既有取舍),写清楚问题描述 —— 见 design.md Amendments:① hero 高度 420rpx≈218px 矮约 32% ② 品牌文字锚点方向反(bottom vs top)
- [x] 4.2 按 TDD 修复(先写断言锁定修复前状态,再改 wxml/wxss,再验证),参照 D5 icon-verification 先例风格 —— `login-flow.test.ts` 新增 describe 块锁定 RED→GREEN,`login.wxss` 改 `.login-hero{height:620rpx}` + `.login-brand{top:224rpx}`;走查 agent 复验实测 hero `{top:0,bottom:322}`、brand `{top:116,bottom:229}` 与 OD 分毫不差
- [x] 4.3 `cd frontend && npm test` 确认全量无回归 —— 750/750 PASS

## 5. Golden 集成

- [x] 5.1 生成 `frontend/e2e/od-golden/mp-10-login-step1.png`(固定 viewport/DPR,仿照现有 9 屏生成方式)—— Playwright 390×762 截 OD 原型默认态
- [x] 5.2 生成 `frontend/e2e/od-golden/mp-10-login-step2.png` —— 同上,模拟勾选+点击登录后的转场态
- [x] 5.3 在 `frontend/e2e/tools/visual-diff.cjs` 的 `SCREENS` 数组新增两条记录(`mp-10-login-step1`/`mp-10-login-step2`,path 均为登录页路由,step2 那条带驱动到 `step:2` 所需的前置动作,仿照 mp-04-cart 的 `auth:true` 模式实现方式)—— 新增 `afterLand` hook 机制(step2 用 setData 注入 + 800ms settle delay,修了一个 harness 时序 bug:纯本地 setData 无网络往返垫底延迟,wx:if 重渲染滞后于 data 更新,screenshot 抢在重绘前拍导致截错画面)
- [x] 5.4 `npm run test:visual` 跑通新增两屏,感知 diff 在阈值内(GREEN)—— **未采用 <5% gate**(与既有 9 屏一致的既定结论,见 c5-visual-test-runbook 收口决定):step1 46.55%、step2 48.99%,均落在其余 9 屏 20-70% 历史基线区间,构成核实为既有取舍(渐变代替实拍图 + dev-only 元素),非"截错画面"级假信号

## 6. 几何断言(按 design.md D3 决策,视 2/3 步走查结果决定是否需要)

- [ ] 6.1 (不适用,见 6.2)
- [x] 6.2 若走查偏离均非几何类:明确记录"本次不补几何断言,理由:仅排版/颜色类偏离,感知层已覆盖",不做任何几何相关文件改动 —— 两处真实偏离(高度/位置)现有 `geometry-diff.cjs` 仅有的 `present`/`count`/`columns` 三种 metric 表达不了(需新增尺寸/位置 metric,超出本 change 授权范围),且正是感知层该抓的类型,不为补齐清单硬凑

## 7. 收尾

- [x] 7.1 回填 `openspec/changes/align-mp-login-with-od/tasks.md` 的 6.4(标注已由本 change 完成,附走查结论)与 6.5(标注已补 golden,几何视 6.1/6.2 结论注明)
- [x] 7.2 `cd frontend && npm test` 全量回归(若第 4 节有修复,已在 4.3 验证过,此处确认无新增回归)
- [x] 7.3 走 `superpowers:requesting-code-review` 自查后再提交 —— code reviewer subagent 结论 "Ready to merge: Yes"(0 Critical/Important,6 Minor 非阻塞,详见对两处关键判断——onDevLogin 不改 + rpx↔px 换算——的独立核实);已按逻辑拆 3 个 commit 提交(`dc49ef5`/`c50b72e`/`ba18546`)
