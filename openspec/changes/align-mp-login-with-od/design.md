## Context

当前登录页(`frontend/pages-sub/user/login/`)是紫色渐变背景 + "开发者登录/微信登录"两个平级按钮的临时实现;OD 设计稿 `mp-10-login.html` 是暖色系品牌视觉 + 用户协议勾选 + "微信授权 → 手机号绑定"两步流程。`User` 聚合(`backend/.../user/domain/User.java`)已有 `phone` 字段,但目前只在收货地址(`Address.phone`)里被写入,用户本人的登录手机号从未有写入路径。已有 `WechatCodeExchanger`(`user/application/`)是 dev/prod 双模式换取 openId 的先例,可以直接复用其模式做手机号换取。项目约束:DDD 四层不可越、跨模块只走 ApplicationService、GraalVM Native 下只能用同步 `RestClient`(不能用响应式 WebClient)、TDD 优先。

## Goals / Non-Goals

**Goals:**
- 登录页视觉/交互与 `mp-10-login.html` 对齐:hero 视觉区、协议勾选(未勾选 shake+toast)、暖色 accent 按钮、两步状态机(微信授权 → 手机号绑定引导)
- 打通手机号绑定闭环:新增最小化后端能力(`WechatPhoneNumberExchanger` + `User.bindPhone` + 鉴权端点),复用 `WechatCodeExchanger` 的 dev/prod 双模式约定
- 「开发者登录」入口保留但视觉降权为不显眼辅助链接,不破坏现有 e2e `login-flow.test.ts`

**Non-Goals:**
- 不新增用户协议/隐私政策静态页面(维持 OD 设计里的占位 toast)
- 不改动 admin-ui 登录(`AdminCookieAuthController`)
- 不改动既有 `/api/auth/wechat-login`、`/api/auth/refresh` 契约(新增端点,非 BREAKING)
- 不做手机号短信验证码二次校验 —— 直接信任微信 `getPhoneNumber` 官方接口解密结果,与行业惯例一致

## Decisions

**1. 手机号绑定端点归属 `api/users/me/phone`,而非 `api/auth/**`。**
`/api/auth/**` 目前语义局限于登录/刷新/登出(公开或换取 token);手机号绑定是已登录用户的资料变更,归入 user 模块 `me` 前缀系列端点(与未来的 `/api/users/me/addresses` 风格一致)更符合现有模块边界,鉴权直接复用 `JwtAuthenticationFilter` 已解出的 `UserPrincipal`。
Alternative considered:放进 `/api/auth/bind-phone`。Rejected——会让 `AuthController` 承担资料变更职责,与其当前"只发/收/吊销 token"的单一职责冲突。

**2. `WechatPhoneNumberExchanger` 仿 `WechatCodeExchanger` 的 dev/prod 双模式。**
- `wechat.enabled=false`:接受 `dev-*` 前缀 code,直接返回由 code 派生的固定测试手机号,不发起真实微信调用
- `wechat.enabled=true`:先用 `cgi-bin/token`(`grant_type=client_credential`)换 access_token(进程内内存缓存 + 过期时间戳,官方限流 2000 次/日、有效期 7200s,不能每次现换),再调官方 `phonenumber.getPhoneNumber` 用 code 换真实手机号
Alternative considered:走旧版 `encryptedData`/`iv` + `session_key` AES 解密方案。Rejected——微信官方现推荐服务端 `getPhoneNumber` 接口(code 直接换号),无需自行维护对称解密逻辑,减少自研安全代码面。

**3. `User.bindPhone(String phone)` 领域方法,只做非空校验,不做格式正则。**
与现有 `addAddress`/`updateAddress` 的轻量校验风格保持一致;项目里 `Address.phone` 现状同样不做格式校验,不引入新的、与其它手机号字段不对称的校验规则。

**4. 前端 Step2 提供真实 `getPhoneNumber` 按钮 + 视觉收敛的 dev fallback。**
`button open-type="getPhoneNumber"` 触发真实微信授权(需企业资质,仅真机/生产可用);devtools/e2e 环境无法触发该原生流程,因此并列一个与"开发者登录"同等视觉权重的"开发者:测试手机号绑定"入口,点击后前端合成 `dev-*` code 调同一端点,保证 e2e 能覆盖完整闭环。
Alternative considered:只接真实 `getPhoneNumber`,不提供 dev fallback。Rejected——会让 Step2 在本地/e2e/未获得企业资质前完全不可测,与现有 `onDevLogin` 先例(线上也保留一条可测试路径)不一致。

**5. 登录页状态机用 Page `data.step` 字段驱动 `wx:if`,而非 OD 原型的 DOM `hidden` 属性切换。**
更贴合小程序 `Page`/`setData` 的既有写法(参考本项目其它多态页面),协议未勾选的 shake 动效用 WXSS `@keyframes` + 动态 class(小程序 WXSS 支持标准 CSS animation,行为等价于 OD 原型)。

**6. 开发者登录视觉收敛为登录卡片底部小字文本链接。**
从"页面中部主按钮"降级为与 OD "暂不登录,先逛逛"同等视觉权重的辅助链接,不改变其绑定的 `onDevLogin` 逻辑,现有 `login-flow.test.ts` 无需改动断言目标(仍是同一个可点击元素,只是样式变化)。

## Risks / Trade-offs

- [Risk] 微信"手机号快速验证组件"接口在生产环境需要额外的小程序资质权限申请,申请未通过前生产环境这条路径不可用 → Mitigation:新增独立配置开关(如 `wechat.phone-binding.enabled`),未开通时 Step2 直接展示"跳过"引导,不阻塞现有登录主流程
- [Risk] access_token 目前用进程内存缓存,若未来从单实例扩容为多实例部署,会重复申请、可能触碰微信频率限制 → Mitigation:当前项目仍是单容器部署,先接受此限制;多实例化时需迁移共享缓存(Mongo/Redis),记为 Open Questions
- [Risk] 登录页新增 Step2 后,4 层 + C5 视觉/几何验证需要覆盖两个状态而非一屏,增加验证维护量 → Mitigation:在 tasks.md 中把 e2e/视觉验证拆成独立任务项,不与功能开发任务混在一起
- [Trade-off] 「开发者登录」与「开发者:测试手机号绑定」两个 dev 专用入口会一并留在生产包里(小程序没有简单的多环境分包剔除机制) → 接受:与现状 `onDevLogin` 一致的既有权衡,后续如需彻底移除可开新 change 单独处理

## Migration Plan

- 新端点是新增能力,不改动既有 `/api/auth/**` 契约,无需数据迁移;`User.phone` 字段已存在于 `UserDocument`,旧文档该字段本就允许为空,无 schema migration 需求
- 部署顺序:后端(新增端点,向后兼容)与前端(登录页重做)可分别上线,不要求同批次发布
- 回滚:前端登录页重做如需回滚,直接 revert 对应 commit(纯页面替换,无数据结构变更);后端新端点如需回滚,停用对应 Controller route 即可,不影响其它模块

## Open Questions

- 微信"手机号快速验证组件"的权限申请状态需要业务侧确认;若尚未具备生产调用资格,`wechat.phone-binding.enabled` 默认应为 `false`,Step2 视觉骨架仍按 OD 对齐,但真实绑定按钮置灰或替换为"敬请期待"提示 —— 留给 tasks 阶段结合业务确认再定
- 是否把登录页两态一并纳入 C5 视觉/几何验证的 golden set(新增 `mp-10-login` 对应截图/几何不变量)—— 建议在 tasks.md 中列为独立可选任务,视本次 sprint 时间预算决定是否一并完成
