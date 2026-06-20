# C5 — mp 视觉验证(感知层)

mp 实截图 vs OD 设计 golden 的 odiff 感知比对,抓"现状偏离 OD / 视觉不可用",驱动 TDD 修复 + 防偏。
当前已落地 **home 一屏 + 感知层**;几何层 + 全 9 屏 + 取代旧静态验证留下游(见 change `sprint-5-c5-visual-verification`)。

## 跑

```bash
# 1) 起微信 DevTools 自动化端口(脚本可自起,无需手动开 IDE)
/Applications/wechatwebdevtools.app/Contents/MacOS/cli auto \
  --project "$(pwd)" --auto-port 9420        # 在 frontend/ 下

# 2)(可选)起后端 + seed —— 让 mp 渲染真实内容,否则是 loading/空态(diff 必然很大)
#    见根 README「后端 + MongoDB」+ backend/seed/

# 3) 跑感知比对
cd frontend
npm run test:visual                 # 全部纳入屏
VISUAL_THRESHOLD=5 npm run test:visual mp-01-home   # 单屏 + 自定阈值(%)
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
