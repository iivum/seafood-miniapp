# Tasks — mp 视觉验证工具(C5)

> 顺序按 design Migration Plan:spike 先行(C1-C4 教训)。本地需微信 DevTools,apply 时自起。

## 1. spike(D3/D1 — 阻塞后续)

- [x] 1.1 **自起 DevTools 成功**:`cli auto --project frontend --auto-port 9420` → AppID wx382d7553c29e617c,**9420 LISTENING**,已登录无需 QR。约束①消除
- [x] 1.2 **connect + 截图链路通**(分步日志):connect 11ms ✅ → switchTab 3s ✅ → **截图 ✅ 780×1524 PNG**(390×762@DPR2 真实 home);`page.$('.home-banner')` 超时——选择器没错(wxml 确有),真因是 **home 内容需后端数据才渲染**(banner/grid 来自 products/categories),后端没跑 → loading 态 → 元素不渲染。几何层前置 = 后端起+seed(同现有 mp e2e fromBackend)
- [ ] 1.3 OD 侧:`get_artifact` 拉 `mp-01-home.html` → Playwright 渲染 golden + 量 bbox(待 §2)
- [x] 1.4 **判定:GO**。三个硬不确定项全证明可行(DevTools 自起 / automator connect / 截图)。感知层 = 截图 pipeline 已通,最具风险的一环反而最稳;几何层需后端渲染内容(编排细节)。**修正 design D1**:感知(截图)证明可端到端,几何需「后端+DevTools+automator」三者就位

> **用户选 option 2:home 一屏 + 感知层先落地,几何 + 全 9 屏 + 取代旧 test 留下游。**

## 2. golden 生成 + 首屏产物

- [x] 2.1(感知 golden 已落地)home golden 经 Playwright MCP 渲 OD `mp-01-home.html`(390×762,scale=css)→ 提交 `e2e/od-golden/mp-01-home.png`。gen-od-golden 脚本化 + 余 8 屏留下游;配方记入 `e2e/tools/README.md`
- [ ] 2.2 `od-geometry/<screen>.json`(几何层,下游)
- [x] 2.3 home golden 提交

## 3. 验证 harness(感知 + 几何均落地)

- [x] 3.1 **几何层落地**(`geometry-diff.cjs` + `od-geometry/<screen>.json`)。**关键踩坑**:automator 0.12.1 元素句柄 API(`page.$`/`$$`/`element.size()/offset()`)在本环境直接超时挂死,`page.outerWxml` undefined(现有 `mp-3layer.test.ts` 用它,实则不绿 → 印证 C5 动机)。**唯一可行路线** = mp 原生 `wx.createSelectorQuery().boundingClientRect()` 经 `mp.evaluate` 在 mp 运行时内跑。metric:present/count/columns(left 聚类)。**AA/DPR/设备框全免疫**。已接 home + category 两屏:**home 锁定 grid 实际 1 列(应 2,20 cell 全宽 373px)+ banner 缺失(banners 数据空),chips/header 正常** —— 精确剥离感知 60% 里的噪声;category 锁定 grid 初始 0 商品(需选中分类)+ header 折叠。`npm run test:geometry` 入口
- [x] 3.2 感知层 `visual-diff.cjs`:automator 截 mp 实图 → sips 归一化到 golden 尺寸 → `odiff-bin`(AA-tolerant)→ 阈值 gate + 产 diff 图。masking 留下游(home 暂无需)
- [x] 3.3 `npm run test:visual` 入口;DevTools 自起(`cli auto`)已验
- [x] 3.4 接 `mp-01-home` 跑通。**修正捕获 bug**:原 `switchTab` 到已激活 tabBar 页不重跑 `onLoad` → 永远截到陈旧空态(假信号 46.47% 恒定)。改 `reLaunch(path)` 关栈重开 → `onLoad` 必触发重新拉后端数据。**后端起 + seed 后真信号 = RED 60.28%**(带真实 55 商品的 home vs OD golden)。产 `mp-01-home-diff.png`
- [x] 3.5 **后端 seed 真信号(下游 ④ 完成)**:`seafood-backend:jvm`(native 镜像 arm64 不匹配本机,exec format error → 换 JVM 镜像)接 `seafood-mongodb` 起;mongoimport 灌 50 商品 + 5 分类 + 2 用户;**修 stale fixtures**(缺 `status` 字段 → `listPublic` 只返 ACTIVE → API 0 条;补 `status="ACTIVE"` + 字符串时间转 ISODate)→ `/api/products` 返 55;mp reLaunch 后 `page.data()` 实测 `categories[4] products[20]` 渲染成功。**结构性偏离实录**(驱动逐屏修):1 列全宽 vs OD 2 列栅格 / 缺 hero banner / 缺品类图标行 / 缺定位头 / 搜索框样式差 / 区块头无 filter tab

## 4. 铺开 + 取代 + 文档

- [x] 4.1 **4 个 tab 页全接入**(SCREENS 参数化):mp-01-home / mp-02-category / mp-04-cart / mp-05-profile。golden 经 Playwright 渲 OD mockup(390×762)提交 `od-golden/`。全 RED:home 60.28% / category 66.38% / cart 53.29% / profile 70.13% —— 4 tab 页全部显著偏离 OD,印证「多屏不可用」。**harness 加固**:① per-screen try/catch(单屏失败不致命)② reLaunch best-effort(cart 等 tabBar 页 reLaunch promise 不 resolve 但页面实已加载 → catch 超时后照常截图)。**余 5 分包带参页**(mp-03 detail / 06 confirm / 07 address / 08 list / 09 detail)需 product/order id + 登录态 + 可复现夹具,留下游 4.1b
- [ ] 4.2 删除/取代 `mp-od-design.test.ts` 静态 grep(下游,待感知/几何铺满)
- [x] 4.3 **改 CLAUDE.md**:已废弃「像素 diff 太脆」旧说法 → 「感知 diff 主门 + 4 层辅」;`e2e/tools/README.md` 记跑法/golden 配方
- [x] 4.4 commit(见下;本 slice)

## 5. 收尾(slice 不归档,留 change 活跃做下游)

- [ ] 5.1 回填 roadmap(待 C5 全量完成)
- [ ] 5.2 归档(待几何 + 全 9 屏 + 删旧 test 完成)

> **下游 backlog**:① 几何层 ✅ **完成**(home+category 两屏,mp.evaluate 原生查询)② 4 tab 页感知 ✅ + 余 5 分包带参页留下游 4.1b ③ 删 `mp-od-design.test.ts`(待)④ ✅ 后端 seed 真信号
>
> **起后端复现**(下游/CI 复跑用):
> ```bash
> # mongo 已在 seafood-mongodb;native 镜像 arm64 不匹配本机 → 用 jvm 镜像
> docker run -d --name seafood-backend --network seafood-miniapp_seafood-network -p 8080:8080 \
>   -e JWT_SECRET=$(openssl rand -base64 48|tr -d '\n'|head -c44) \
>   -e JWT_ADMIN_SECRET=$(openssl rand -base64 48|tr -d '\n'|head -c44|rev) \
>   -e ADMIN_BOOTSTRAP_PASSWORD='SeafoodAdmin#2026' \
>   -e MONGODB_URI=mongodb://mongodb:27017/seafood -e SPRING_MONGODB_URI=mongodb://mongodb:27017/seafood \
>   -e SPRING_PROFILES_ACTIVE=docker seafood-backend:jvm
> # seed(fixtures 缺 status,需补):mongoimport 后 updateMany 置 status=ACTIVE + 时间转 ISODate
> ```
