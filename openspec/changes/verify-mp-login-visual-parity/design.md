## Context

`frontend/e2e/tools/visual-diff.cjs` 与 `geometry-diff.cjs` 都用**显式硬编码的屏幕清单**(`SCREENS` 数组 / 路径 map),不是自动扫描 `od-golden/`/`od-geometry/` 目录 —— 已核实 visual-diff.cjs:39-58 枚举 mp-01~mp-09 共 9 屏,geometry-diff.cjs:39-42 只枚举 mp-01/02/04/05 四屏(9 屏里目前只有 4 屏有几何断言,其余 5 屏尚无,是既有的部分覆盖状态,非本 change 引入)。

登录页(`frontend/pages-sub/user/login/login.js`)状态机核心字段是 `data.step`(1|2)。`onDevLogin()` 方法(login.js:57)是任务 5.3 就为 E2E/本地覆盖 Step2 特意留的开发者入口 —— 调用后不经真实微信授权,直接把 `step` 置 2 并填充测试数据。这意味着走查 Step2 **不需要**沿用 D5 verification 那套"globalData.userInfo 伪造 + wx.request mock 组合注入"(那是为了绕过其他页面的路由守卫/鉴权而设计的重手段);登录页本身自带的 dev 入口就是最小、最贴合真实状态机的路径。

`mp-e2e-expert` agent 的 API 可靠性矩阵(`.claude/agents/mp-e2e-expert.md` §三)已确认 `page.callMethod` 挂死,唯一可靠调用页面方法的方式是 `mp.evaluate` + `getCurrentPages()` 直接拿页面实例调方法。

## Goals / Non-Goals

**Goals:**
- 登录页 Step1/Step2 两态截图与 `mp-10-login.html` 原型走查比对,发现明显偏离时在本 change 内直接修复(不新开 change,与 D5 icon-verification 先例一致)
- 补 `frontend/e2e/od-golden/mp-10-login.png`,接入 `visual-diff.cjs` 既有的 `SCREENS` 显式注册模式
- 回填 `align-mp-login-with-od/tasks.md` 6.4/6.5

**Non-Goals:**
- 不建新的 golden/geometry 发现机制(如自动扫描目录)—— 沿用现有显式注册惯例,避免为一屏引入平行机制
- 不强制补几何断言:9 屏里已有 5 屏(mp-03/06/07/08/09)没有几何覆盖,登录页是静态两态排版(无 grid/无滚动列表),感知层足以捕捉明显偏离;是否补几何留给走查后按信号价值判断,而非教条式"每屏必配"
- 不改动手机号绑定的真实业务逻辑/后端端点(那是 `align-mp-login-with-od` 已完成并合并的范围)

## Decisions

**D1:登录页加入 `SCREENS` 数组,而非新建脚本或自动发现机制**
沿用 `visual-diff.cjs:39-58` 的既有约定(每屏一条 `{ name, path, auth?, ... }`),与 mp-01~mp-09 保持同一套断言/报告路径。理由:一致性优先于"顺手做个通用发现机制"的过度工程——9 屏都是手工注册,登录页没有特殊到需要打破这个模式。

**D2:Step1 用默认 `reLaunch`,Step2 用 `mp.evaluate` 调 `onDevLogin`,不用 globalData/wx.request 注入组合拳**
Step1 是登录页默认态(`step:1`),`reLaunch` 到 `/pages-sub/user/login/login` 即得,不需要任何注入。Step2 调用页面自带的 `onDevLogin()`(经 `mp.evaluate` + `getCurrentPages()` 取页面实例直接调,规避已知挂死的 `page.callMethod`),这是页面作者专门为此场景留的入口,比 D5 那套"伪造鉴权穿透路由守卫"更贴合登录页本身的状态机、也更简单。

**D3:几何断言留待走查后决定,不预先承诺**
登录页是排版为主的静态两态页(hero 文案区 + 按钮 + 协议勾选行 / 头像昵称 + 按钮 + 跳过链接),没有 grid、无限滚动或多列布局这类几何层最擅长捕捉的"布局崩"风险类别。先做感知层走查,如果发现的偏离恰好是几何类(如元素错位/重叠/尺寸问题),再补 `od-geometry/mp-10-login.json`;如果偏离只是颜色/字重/间距这类,感知层已经够用,不为了"补齐清单"而写一份价值存疑的几何断言。

**D4:走查中发现的真实视觉缺陷,在本 change 内直接修复**
与 D5 icon-verification 先例一致(该轮验证顺带修了 selectedId 死绑定 + 勾选徽章遮挡 + 裸 hex 阴影,均在同一 change 内完成,未拆分新 change)。若走查发现登录页与 OD 原型有真实偏离,按 TDD 修复并入本 change 的 tasks,而不是另开一轮"发现问题但不修"的 change。

## Risks / Trade-offs

- **[风险] Step2 截图依赖 dev-only 路径,不是真实微信授权流程** → **缓解**:与 mp-04/mp-07/mp-08/mp-09 现有 golden 截图的既定惯例完全一致(它们也都靠 `auth: true` 之类的 dev/mock 路径达到目标状态,从未用真实微信授权),不是本 change 引入的新妥协。
- **[风险] `mp_screenshot` 端口老化后无限挂死(`mp-e2e-expert` 已知坑)** → **缓解**:直接遵循 agent 定义 §四 的重启流程(`cli quit` + 重启 `cli auto`),截图前按 §三 加看门狗。
- **[风险] geometry 断言留白可能被误读为"漏做"** → **缓解**:proposal/design 已明确这是价值判断后的主动决定,且与现有 9 屏里 5 屏本就没有几何覆盖的既有状态一致,不是本 change 制造的新缺口。

## Amendments(执行期修正)

**D2 的技术前提被实机走查(mp-e2e-expert)推翻,已改用替代方案**:D2 假设"`onDevLogin()` 调用后不经真实微信授权,直接把 `step` 置 2"——实机调用后发现 `onDevLogin()` 内部无条件 `.then(() => this.handleLoginSuccess())`,直接登录成功跳首页,完全不像 `onWxLogin()` 那样按 `user.phone` 分支进 `enterPhoneBindStep`。核实这是 `onDevLogin()` 本身的既有产品行为(本地开发者高频用它一键跳过整个登录流程,不是遗漏),`login-flow.test.ts` 现有断言也只查它调了 `wx.login(`,不依赖后续跳转行为,故**不修改 `onDevLogin()` 本身**(避免改变本地开发者的高频路径)。DevTools 里同样走不通真实 `onWxLogin()` 路径(模拟 `wx.login()` 返回的 code 不带 `dev-` 前缀,后端 `wechat.enabled=false` 时 409 拒绝)。改用:`visual-diff.cjs` 的 `mp-10-login-step2` 条目直接 `page.setData({step:2, userNickname, userAvatarInitial})` 注入登录页自身状态字段渲染该态,与 mp-04/06/07/08/09 既有的 `auth`/`seed` 注入(`injectAuth` 等)同类——都是"真实业务路径不可达时,直接注入目标渲染状态"的既定做法,不新开先例。

**走查发现 2 项真实视觉偏离,已按 D4 在本 change 内修复**(`login.wxss`,TDD 断言见 `frontend/src/__tests__/login-flow.test.ts` 新增 describe 块):
1. `.login-hero` 高度 420rpx(390 宽下≈218px)比 OD `.hero-visual` 322px 矮约 32%(明显偏离)→ 改 620rpx(≈322px)
2. `.login-brand` 锚点方向反了:mp 原用 `bottom:64rpx`(贴 hero 底部),OD `.brand-mark` 用 `top:116px`(贴 hero 顶部,状态栏下方深色渐变区)→ 改 `top:224rpx`

协议勾选框尺寸、按钮尺寸/配色、skip 链接、Step2 头像行/按钮/skip 链接均实测与 OD 一致或仅轻微差异(≤6%),不需要修复。
