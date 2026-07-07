## Why

小程序登录页(`frontend/pages-sub/user/login/`)当前是紫色渐变背景 + "开发者登录/微信登录"两个平级按钮的临时实现,与 Open Design 设计稿 `mp-10-login.html`(暖色系品牌视觉 + 协议合规勾选 + 微信授权后引导绑定手机号的两步流程)差距明显。登录页是用户进入小程序后最先接触的品牌触点,也是后续手机号触达能力(订单进度/到货提醒)的入口;当前实现既未体现品牌视觉,也没有法务要求的用户协议/隐私政策勾选,更缺手机号采集入口。现有 4 屏(home/category/product-detail/cart 等)已纳入 C5 视觉验证 golden set,登录页尚未覆盖,借这次改动一并补齐。

## What Changes

- 小程序登录页按 `mp-10-login.html` 重做视觉与交互:顶部 hero 图 + 品牌文案区、用户协议/隐私政策勾选(未勾选点击登录时 shake + toast 提示)、微信一键登录按钮改为设计稿暖色 accent 视觉
- 登录后新增 Step2 手机号绑定引导:展示头像/昵称 + "微信授权成功",引导用户一键授权手机号;支持"暂不绑定,进入首页"跳过
- **新增后端手机号绑定能力**:`User` 聚合已有 `phone` 字段但当前没有写入路径 —— 新增 `WechatPhoneNumberExchanger`(比照 `WechatCodeExchanger` 模式:`wechat.enabled=false` 时接受 `dev-*` mock code 直接返回测试手机号;`wechat.enabled=true` 时调微信 `phonenumber.getPhoneNumber` 官方接口用 code 换真实手机号)+ `UserService` 绑定手机号的应用方法 + 鉴权态端点
- 前端对接 `button open-type="getPhoneNumber"` 拿 code,调新端点完成绑定
- 「开发者登录」入口保留,但视觉上收敛为不显眼的辅助入口(不出现在主视觉里),不影响现有 e2e `login-flow.test.ts` 依赖的 dev-login 流程
- 用户协议/隐私政策链接维持 OD 设计里的占位行为(点击 toast「详情开发中」),不新增静态协议页面

## Capabilities

### New Capabilities
(无 — 本次是对已有登录流程能力的视觉与交互扩展,未引入全新能力域)

### Modified Capabilities
- `mini-program`: "Authentication and session" requirement 扩展 —— 微信登录前需完成用户协议/隐私政策勾选;授权成功后进入可跳过的手机号绑定步骤;开发者登录入口以不显眼形式保留,行为不变
- `backend-api`: 新增客户端手机号绑定端点(鉴权用户可通过微信 `getPhoneNumber` code 绑定/更新手机号),复用 `WechatCodeExchanger` 的 dev/prod 双模式约定

## Impact

- **前端**:`frontend/pages-sub/user/login/{login.wxml,login.js,login.wxss}` 全面重写;`frontend/src/features/auth/{store.ts/js,api 客户端}` 新增 `bindPhone` 方法
- **后端**:`backend/src/main/java/com/seafood/user/` 新增 `application/WechatPhoneNumberExchanger.java`、`UserService` 绑定手机号方法、对应 Controller 端点 + Request/Response DTO;不改动既有 `/api/auth/wechat-login`、`/api/auth/refresh` 契约,非 BREAKING
- **测试**:`frontend/e2e/`(`login-flow.test.ts`、C5 `od-golden`/`od-geometry`)需要新增登录页覆盖;后端新增手机号绑定端点的单元/集成测试
- **不涉及**:管理后台登录(`AdminCookieAuthController`)、用户协议/隐私政策静态页面(维持占位)
