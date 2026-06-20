# C5 — mp 视觉验证(感知层 + 几何层)

抓"现状偏离 OD / 视觉不可用",驱动 TDD 修复 + 防偏。两层互补:
- **感知层**(`visual-diff.cjs`):mp 实截图 vs OD golden 的 odiff 像素比对,抓"外观偏离"。
  但掺设备框/状态栏/图片噪声(home 实跑 60%,大半是噪声)。
- **几何层**(`geometry-diff.cjs`):量 mp 实际渲染的**结构不变量**(区域存在性、栅格列数),
  **AA/DPR/设备框完全免疫**,精确剥离噪声、锁定"布局崩没崩"。非 flaky,直接驱动修复。
  例:home 几何锁定 `grid 实际 1 列(应 2 列)+ banner 缺失`,而 chips/header 正常。

已落地 **4 tab 页感知 + home/category 几何**;余 5 分包带参页 + 全屏几何留下游
(见 change `sprint-5-c5-visual-verification`)。

## 几何层关键实现(踩坑)

automator 0.12.1 的元素句柄 API(`page.$` / `page.$$` / `element.size()` / `element.offset()`)
在本环境**直接超时挂死**,`page.outerWxml` 干脆 undefined。改用 mp **原生布局查询**
`wx.createSelectorQuery().boundingClientRect()`,经 `mp.evaluate(fn)` 在 mp 运行时内执行 ——
稳定返回真实 rect。这是几何层唯一可行路线。`od-geometry/<screen>.json` 存 OD 期望不变量
(`present` / `count` / `columns`),harness 量 mp 实际比对。

## 跑

```bash
# 1) 起微信 DevTools 自动化端口(脚本可自起,无需手动开 IDE)
/Applications/wechatwebdevtools.app/Contents/MacOS/cli auto \
  --project "$(pwd)" --auto-port 9420        # 在 frontend/ 下

# 2)(强烈建议)起后端 + seed —— 让 mp 渲染真实内容,否则截到 loading/空态(假信号)
#    见根 README「后端 + MongoDB」+ backend/seed/
#    gotcha-A:本机 Apple Silicon,CI 产的 seafood-backend:native 是 linux/amd64 ELF
#             → exec format error 崩溃。用 seafood-backend:jvm(arm64,JAR 架构无关)。
#    gotcha-B:backend/seed/fixtures 是 stale 的(只有旧字段 onSale,缺 status)。
#             listPublic 只返 status==ACTIVE → API 返 0 条。seed 后须:
#             db.products.updateMany({}, [{$set:{status:"ACTIVE",
#               createdAt:{$toDate:"$createdAt"}, updatedAt:{$toDate:"$updatedAt"}}}])

# 3) 跑比对
cd frontend
npm run test:visual                 # 感知层(全部纳入屏)
VISUAL_THRESHOLD=5 npm run test:visual mp-01-home   # 感知单屏 + 自定阈值(%)
npm run test:geometry               # 几何层(结构不变量,框/AA/DPR 免疫)
npm run test:geometry mp-01-home    # 几何单屏
```

退出码:任一屏 diff% > 阈值 → 非零(RED,驱动修复);全 ≤ 阈值 → 0(GREEN,防偏)。
产物:`screenshots/<screen>-diff.png`(差异图,定位偏离区域)。

## OD golden 怎么来(SoT = Open Design 项目)

golden = OD 项目 `686e3434`(9 张 mp HTML mockup)渲染到 mp viewport 的参照图,提交在 `e2e/od-golden/`。
生成(按需,OD 设计更新时重跑):

1. OD daemon previewUrl:`get_project` → `previewUrl` 的 host(如 `http://127.0.0.1:49180`),
   单屏 raw URL = `<host>/api/projects/686e3434-.../raw/mp-01-home.html`
2. 浏览器(Playwright)resize 到 `390×762` → 打开该 URL → viewport 截图(scale=css)→
   存 `e2e/od-golden/<screen>.png`(390×762)

> 归一化:mp 实截图是 780×1524(DPR2),harness 用 macOS 内置 `sips` 缩到 390×762 对齐 golden 再 odiff。

## 文件

- `visual-diff.cjs` — harness:capture mp 实截 → sips 归一化 → odiff vs golden → 阈值 gate + 报告
- `capture-mp.cjs` — 单独捕获某屏 mp 实截图(调试用)
- `spike-c5.cjs` — §1 spike 探针(DevTools 自起 + automator 链路验证)
