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

### 一键(推荐)

```bash
cd frontend
bash e2e/tools/run-visual.sh              # 起齐依赖(幂等,已就绪自动跳过)+ 感知 + 几何
bash e2e/tools/run-visual.sh visual       # 只感知
bash e2e/tools/run-visual.sh geometry mp-01-home   # 几何单屏
RESEED=1 bash e2e/tools/run-visual.sh     # 强制重灌 seed
```
脚本自动:① 起 DevTools 自动化端口(若未监听)② 起 mongodb + seed(若空,含 stale fixtures 的 status 修复)③ 起后端 jvm 镜像(若 8080 未 200)④ 跑测。
前置仍需:微信 DevTools 已装 + 已登录 + 项目已导入(GUI 应用,脚本代办不了)。

### 手动分步

```bash
# 1) 起微信 DevTools 自动化端口
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

## mp DevTools console 实时监控（console-watch.cjs）

诊断/复验 mp 端行为时(尤其是接线新组件、修事件契约类 bug 后),不要只靠肉眼看截图 ——
静默失败(如未注册的自定义标签、事件 detail 解构错误)往往**不抛异常也不报 console 错误**,
但也有一部分问题会在 console 留痕(`console.warn`/`console.error`/未捕获异常)。

```bash
cd frontend
# 先起 DevTools 自动化端口(同上)
npm run watch:console                 # 常驻监控,warn/error/exception 逐行输出到 stdout
CONSOLE_WATCH_ALL=1 npm run watch:console   # 连 console.log 也要看时
```

原理:`miniprogram-automator` 的 `MiniProgram` 实例内部把 DevTools 协议的
`App.logAdded` / `App.exceptionThrown` 桥接成 `mp.on('console', ...)` /
`mp.on('exception', ...)` 两个 EventEmitter 事件(已读源码确认,只有这两个事件,
没有 `close`/`disconnect` 可订阅)。本脚本订阅后逐行打印,可以喂给任意"tail 一个
命令的 stdout"式监控工具(如 Claude Code 的 Monitor),做到"小程序端一报错就实时
知道",比手动轮询 `weapp-dev` MCP 的 `mp_getLogs` 更不容易漏掉检查点之间发生的异常。

已知限制:DevTools 自动化端口长时间运行后偶尔会进异常状态(实测:跑几十次截图后
`mp.screenshot()` 开始报 "fail to capture screenshot",但这不会触发本脚本的任何
事件,因为库本身没有断连通知)。如果长时间没输出但明确知道 mp 端有操作发生,
先确认自动化端口本身健康(必要时重启 `cli auto` 进程),再重启本脚本。

## DevTools 进程日志监控(编译期错误,devtools-log-watch.cjs)

为什么需要:编译期/组件解析错误(如 `miniprogram_npm` 缺失)不经过 automator 运行时事件桥,只写进 DevTools 自身进程日志 `WeappLog/stderr.log`——`console-watch.cjs` 完全够不到,本工具直接 tail 该文件补上。

怎么跑:`node e2e/tools/devtools-log-watch.cjs`(`WEAPP_WATCH_STDOUT=1` 连 stdout.log;`WEAPP_LOG_DIR=...` 显式指定目录)。

分工:`console-watch.cjs` 盯运行时,本工具盯编译期,建议配 Monitor 工具并列常驻。

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
- `console-watch.cjs` — mp DevTools console 实时监控(warn/error/exception 逐行输出),见上方独立章节
- `devtools-log-watch.cjs` — DevTools 进程日志(编译期错误)tail 工具,补 console-watch.cjs 的盲区,见上方独立章节
