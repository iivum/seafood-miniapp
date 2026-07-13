## 1. 后端 — User 聚合手机号绑定领域能力

- [x] 1.1 为 `User.bindPhone(String phone)` 写单测(`UserTest`):非空/blank 手机号抛 `DomainException`;合法手机号返回携带新 `phone` 的新 `User` 实例,其余字段不变
- [x] 1.2 实现 `User.bindPhone(String phone)`(比照 `addAddress`/`updateAddress` 的轻量校验风格,不做格式正则)

## 2. 后端 — WechatPhoneNumberExchanger(dev/prod 双模式)

- [x] 2.1 写 `WechatPhoneNumberExchangerTest`:`wechat.enabled=false` 时,`dev-*` 前缀 code 返回由 code 派生的确定性测试手机号;非 `dev-*` 前缀 code 抛 `DomainException`
- [x] 2.2 写 `wechat.enabled=true` 场景的单测(mock `RestClient`):access_token 换取成功 + `phonenumber.getPhoneNumber` 换号成功;access_token/换号任一失败抛 `DomainException`;access_token 在有效期内不重复请求(缓存命中)
- [x] 2.3 实现 `WechatPhoneNumberExchanger`(仿 `WechatCodeExchanger` 结构):`cgi-bin/token` 换 access_token + 进程内内存缓存(含过期时间戳),`phonenumber.getPhoneNumber` 换号,dev 模式短路逻辑

## 3. 后端 — 应用服务 + API 端点

- [x] 3.1 写 `UserService` 绑定手机号方法的单测:调用 `WechatPhoneNumberExchanger` 换号 → 调用 `User.bindPhone` → 持久化 → 返回 `UserResponse`
- [x] 3.2 实现 `UserService` 绑定手机号方法
- [x] 3.3 写 Controller 层测试:`UserPhoneControllerTest` 验证 self-scoped 委托(身份取自 principal、code 透传);dev/prod 双模式与错误码已在 `WechatPhoneNumberExchangerTest`/`UserServiceTest` 覆盖;未鉴权(401)由既有 `@PreAuthorize("isAuthenticated()")` + `SecurityConfig` 的 `/api/users/**` 白名单 + JWT filter 链保证(同 `AddressControllerTest` 既定惯例,不在 controller 单测里重复验证)
- [x] 3.4 实现 `PATCH /api/users/me/phone` 端点(`UserPhoneController`)+ `PhoneBindRequest`/复用 `UserResponse`,接入 `UserPrincipal` 鉴权;确认 `/api/users/**` 既有白名单已覆盖新路径,无需改 `SecurityConfig`
- [x] 3.5 新增 `wechat.phone-binding.enabled` 配置开关(默认 `false`,对齐 design.md 的生产资质未就绪风险缓解),仅在 `wechat.enabled=true` 时生效;开关关闭时抛 `DomainException`(映射 `code=DOMAIN`)而非误导性成功

## 4. 前端 — 登录页视觉重做(Step1:hero + 协议 + 微信登录)

- [x] 4.1 重写 `login.wxml`:hero 视觉区(品牌图 + eyebrow + 标题文案)、用户协议/隐私政策勾选行、微信一键登录按钮
- [x] 4.2 重写 `login.wxss`:对齐 OD 暖色 token(复用 `frontend/src/shared/tokens/tokens.wxss` 的 `--accent`/`--bg`/`--muted` 等,而非重复定义)、协议未勾选的 shake `@keyframes`、按钮圆角/间距;hero 暂无产品实拍图素材,用品牌暖色渐变替代 OD 原型的照片背景
- [x] 4.3 重写 `login.js`:`data.step` 状态机骨架、consent 勾选状态、未勾选点击登录时触发 shake class + toast 阻断(不调 `wx.login`)
- [x] 4.4 「开发者登录」收敛为登录卡片底部小字文本链接,保留原有 `onDevLogin` 绑定与行为不变

## 5. 前端 — Step2 手机号绑定交互 + auth store

- [x] 5.1 `frontend/src/features/auth/store`(ts + js shim 成对)新增 `bindPhone(code)` 方法,调用 `PATCH /api/users/me/phone`(经 `UserAPI.bindPhone`,同样 ts+js 成对);`StoredUser` 加 `phone?: string` 字段
- [x] 5.2 `login.wxml`/`login.js` 新增 Step2:头像/昵称 + "微信授权成功"提示、`button open-type="getPhoneNumber"` 一键获取手机号,`bindgetphonenumber` 回调里取 `code` 调 `bindPhone`
- [x] 5.3 新增视觉收敛的"开发者:测试手机号绑定"入口(与「开发者登录」同等权重),点击时合成 `dev-*` code 调 `bindPhone`,供本地/e2e 覆盖 Step2
- [x] 5.4 "暂不绑定,进入首页"跳过链接:不调用 `bindPhone`,直接进入首页,登录态不受影响

## 6. 测试与验证

- [x] 6.1 更新 `frontend/src/__tests__/login-flow.test.ts`,覆盖 `specs/mini-program/spec.md` 新增场景:未勾选协议阻断登录、Step2 展示与跳过、开发者登录入口仍可用(15/15 新增+既有断言全绿)
- [x] 6.2 后端跑 `cd backend && ./gradlew test check`,确认新增用例全绿且 ArchUnit/`checkNoRefreshScope` 无回归(669/669,含顺带修复的 OpenAPI 契约漂移——见下方说明)
- [x] 6.3 前端跑 `cd frontend && npm test`,确认无回归(648/648,`TZ=UTC` 下全绿;唯一一处失败在无 `TZ=UTC` 时出现,是既存的、与本次改动无关的环境依赖问题)
- [x] 6.4 用 `weapp-dev` mcp 在开发者工具内实机走查登录页 Step1/Step2 两态截图,与 `mp-10-login.html` 做视觉比对,记录明显偏离项 —— **已由 `verify-mp-login-visual-parity` 完成**(2026-07-13,`mp-e2e-expert` agent 实机走查):发现 2 项真实偏离并已修复——① `.login-hero` 高度 420rpx(≈218px)比 OD 322px 矮约 32% → 改 620rpx;② `.login-brand` 锚点方向反了(mp 原 `bottom:64rpx` 贴底,OD `top:116px` 贴顶)→ 改 `top:224rpx`。协议勾选框/按钮尺寸/skip 链接/Step2 头像行均实测与 OD 一致或仅轻微差异,未修。另发现 `onDevLogin()` 无法驱动进入 Step2(是其"一键直登"既有产品行为,非 bug,未改;golden 捕获改用直接 setData 注入)、OD 原型 footer 版权文案 mp 未渲染(留待后续决策,不在此次范围)。详见 `openspec/changes/verify-mp-login-visual-parity/design.md` Amendments 章节。
- [x] 6.5(可选,视 sprint 时间预算)将登录页两态纳入 C5 视觉/几何验证 golden set(`frontend/e2e/od-golden/`、`od-geometry/`),补充对应断言 —— **已由 `verify-mp-login-visual-parity` 完成**(2026-07-13):`od-golden/mp-10-login-step1.png` + `mp-10-login-step2.png` 已生成并接入 `frontend/e2e/tools/visual-diff.cjs` 的 `SCREENS`(`npm run test:visual` 可跑,当前 diff 46.55%/48.99%,与其他 9 屏 20-70% 历史基线一致,感知层非 gate)。几何断言(`od-geometry/`)判断不补:两处真实偏离(高度/位置)现有 `geometry-diff.cjs` 的 `present`/`count`/`columns` 三种 metric 表达不了,且正是感知层该抓的类型,不为补齐清单硬凑。

## 7. 收尾

- [x] 7.1 若新增 `wechat.phone-binding.enabled` 配置项,同步更新 `CLAUDE.md` 环境变量表(新增 `WECHAT_PHONE_BINDING_ENABLED`)
- [x] 7.2 走 `superpowers:requesting-code-review` 完成自查后再提 PR —— 结论 "With fixes";0 Critical,3 Important 已处理(补 `login-page-behavior.test.js` 真实行为测试 + `SecurityFilterChainOrderIT` 新回归用例;OpenAPI 契约漂移拆分留到实际提交时按 commit 处理),4 Minor 记录未处理(均为非阻塞/待跟进项)
