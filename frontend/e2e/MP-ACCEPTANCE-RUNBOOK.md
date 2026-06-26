# MP 验收 Runbook — mp-blocker-fix 7 项手验

> **目的**:配合 `e2e/mp-blocker-fix.test.ts`(静态 + API 自动验收)+ `e2e/mp-e2e-live.test.ts`(真机 e2e placeholder,待 MP MCP 稳定后启用)。
> 本 runbook 是**人手逐项跑**的清单 — 静态测试 PASS 只能证明代码层面 OK,真机渲染和交互**仍需人手过一遍**。

**前置**:
- 后端起着 (`localhost:8080`,且 seed 数据已灌,`curl localhost:8080/api/products?page=0&size=5` 应返 `totalElements > 0`)
- 微信 DevTools 起着(`cli auto --project frontend --auto-port 9420`)
- 微信开发者工具已登录(扫码)

---

## 7 项验收清单

### 1. P0-1 启动崩 — 首页 reLaunch 无 TypeError

**步骤**:
1. 清 storage:`wx.clearStorageSync()`(DevTools → Console 执行,或重启后自然清)
2. 点编译 → 首页加载(`pages/index/index`)

**期望结果**:
- Console **无** `TypeError: request is not a function` 错误
- 首页正常显示(banner / chips / 商品列表)

**通过标准**:Console 错误列表不含 `TypeError.*request is not a function`

**对应静态测试**:`mp-blocker-fix.test.ts` §5(app.js 解构)

---

### 2. P0-2 Skyline 降级 — 无 Skyline CSS 警告

**步骤**:
1. 首页加载完成后,DevTools Console 切到 Warnings 标签
2. 滚动页面触发更多渲染(vant chips、商品列表)

**期望结果**:
- Console **无** `Skyline` 警告
- **无** `object-fit` / `-webkit-line-clamp` / `@font-face.*unicode-range` 等 Skyline 不支持 CSS 警告
- 商品图正确显示(`object-fit: cover` 效果由 WebView 实现)

**通过标准**:Console Warnings 列表不含 `Skyline|object-fit|-webkit-line-clamp|@font-face.*unicode-range`

**对应静态测试**:`mp-blocker-fix.test.ts` §4(4 JSON 配置无 skyline)

---

### 3. P1 裂图 — login 页 logo 可见

**步骤**:
1. 清 storage
2. 跳 login 页:`wx.reLaunch({ url: '/pages-sub/user/login/login' })`(或点我的 → 登录)

**期望结果**:
- login 页顶部 logo 图**正确显示**(`frontend/images/logo.png`,1.0 KB,蓝色品牌 logo)
- 不是裂图占位 / 不是空白

**通过标准**:视觉上 logo 图渲染正常,非裂图

**对应静态测试**:`mp-blocker-fix.test.ts` §2(logo.png size > 1024)+ §3(default-avatar.png size > 200)

---

### 4. P1 鉴权 — 未登录点 addToCart 跳 login

**步骤**:
1. 清 storage(`wx.clearStorageSync()`)
2. 回首页(`wx.reLaunch({ url: '/pages/index/index' })`)
3. 点任一商品的「加入购物车」按钮

**期望结果**:
- toast「请先登录」
- 跳到 `/pages-sub/user/login/login?redirect=/pages/index/index`
- 当前页(history stack)保留为首页(登录成功后回首页)

**通过标准**:
- URL 跳转含 `?redirect=` query
- toast 显示「请先登录」

**对应静态测试**:`mp-blocker-fix.test.ts` §6(addToCart 鉴权)

---

### 5. P1 鉴权 — 未登录进 order-list 跳 login

**步骤**:
1. 清 storage(`wx.clearStorageSync()`)
2. 直接进订单列表:`wx.reLaunch({ url: '/pages-sub/order/order-list/order-list' })`

**期望结果**:
- 跳到 `/pages-sub/user/login/login?redirect=/pages-sub/order/order-list/order-list`
- 无 403 错误(订单 API 在未登录时不会被调)

**通过标准**:
- URL 跳转含 `?redirect=` query
- DevTools Network 看不到 `GET /api/orders` 请求(因守卫先于 fetchOrders 触发)

**对应静态测试**:`mp-blocker-fix.test.ts` §7(order-list onShow 鉴权)

---

### 6. P2 登录 — dev-login 成功 + storage 有 accessToken

**步骤**:
1. 接 5 步骤之后(在 login 页,`redirect=/pages-sub/order/order-list/order-list`)
2. 点「开发者登录」按钮(`bindtap=onDevLogin`)
3. 等 ~1 秒(loading → toast)

**期望结果**:
- toast「登录成功」
- 自动跳回原页(order-list)
- `wx.getStorageSync('accessToken')` 返非空字符串
- `wx.getStorageSync('userInfo')` 含 userId / nickname

**通过标准**:
- accessToken 长度 > 100(JWT 形式)
- 当前页面路径**不是** `/pages-sub/user/login/login`

**对应静态测试**:本任务未单独锁(P2 改造的单元测试在 `frontend/src/__tests__/login-flow.test.ts` 6/6 PASS)

---

### 7. 完整 flow — 登录 → 加购 → 购物车有数据

**步骤**:
1. 完成 6 后(已登录,token 在 storage)
2. 回首页(`wx.switchTab({ url: '/pages/index/index' })`)
3. 点任一商品的「加入购物车」按钮
4. 切到购物车 tab(`wx.switchTab({ url: '/pages/cart/cart' })`)

**期望结果**:
- 加购 toast「已加入购物车」
- 购物车页显示**刚刚加的商品**(1 件)

**通过标准**:
- 购物车页 items 数组 length ≥ 1
- 第 1 件商品的 productId = 刚才点击的商品 id
- 后端 `POST /api/cart/items`(带 `Authorization: Bearer <accessToken>`)返 200

**对应静态测试**:本任务未单独锁(完整 flow 是 4 + 6 的组合;单元 + 集成已有覆盖)

---

## 跑法总结

### 一键自动验收(静态)

```bash
cd frontend
TZ=UTC ./node_modules/.bin/jest e2e/mp-blocker-fix.test.ts \
  --no-coverage --testPathIgnorePatterns='/node_modules/'
```

预期 14/14 PASS(后端空时 §1 跳过 — 见警告日志)。

### 真机 e2e(待 MP MCP 稳定后启用)

```bash
# 取消 mp-e2e-live.test.ts 中的 it.skip → it.only,然后:
cd frontend
WS_ENDPOINT=ws://127.0.0.1:9420 \
  TZ=UTC ./node_modules/.bin/jest e2e/mp-e2e-live.test.ts --runInBand
```

### 手验(本 runbook)

按上面 7 项**逐项跑**,DevTools Console + Network + WXML inspector 配合观察。

---

## 失败排查

| 现象 | 排查方向 |
|---|---|
| §1 console `TypeError: request is not a function` | 检查 `app.js` 是否有非解构 `const request = require('./utils/request.js')`(应全是 `const { request } = ...`) |
| §2 Skyline 警告 | 检查 4 JSON 配置是否又被误加 `renderer: 'skyline'` / `componentFramework: 'glass-easel'` |
| §3 logo 裂图 | 确认 `frontend/images/logo.png` 存在 + size > 1024 + 不是 0 字节(可 `ls -la frontend/images/logo.png`) |
| §4 addToCart 不跳 login | 检查 `pages/index/index.js` `addToCart` 函数体是否有 `wx.getStorageSync('accessToken')` + `wx.navigateTo({url: '.../login?redirect=...'})` |
| §5 order-list 不跳 login | 检查 `pages-sub/order/order-list/order-list.js` `onShow` 函数体顶部 8 行内是否有 `wx.getStorageSync('accessToken')` |
| §6 dev-login 失败 | 后端是否认 `dev-` 开头的 code(需 `wechat.enabled=false` + `WechatCodeExchanger` 正常 exchange);Console 看 `[login] 失败` 错误详情 |
| §7 加购无数据 | Network 看 `POST /api/cart/items` 是否 200;`Authorization` header 是否带 token;后端 MongoDB cart 集合是否有数据 |

---

## 相关文件

- 静态 + API 自动验收: `frontend/e2e/mp-blocker-fix.test.ts`
- 真机 e2e placeholder: `frontend/e2e/mp-e2e-live.test.ts`
- P0-1 单元: `frontend/src/__tests__/app-launch-shim.test.ts`
- P0-2 单元: `frontend/src/__tests__/renderer-config.test.ts`
- P1 资源: `frontend/src/__tests__/image-assets.test.ts` / `font-assets.test.ts`
- P1 addToCart 单元: `frontend/src/__tests__/addtocart-auth-guard.test.ts`
- P1 order-list 单元: `frontend/src/__tests__/orderlist-auth-guard.test.ts`
- P2 login 单元: `frontend/src/__tests__/login-flow.test.ts`
- 任务计划: `.superpowers/sdd/task-7-brief.md`
- Plan 总览: `docs/superpowers/plans/2026-06-26-mp-blocker-fix.md`