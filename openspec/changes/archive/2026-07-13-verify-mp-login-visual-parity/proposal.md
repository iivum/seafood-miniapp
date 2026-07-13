## Why

`align-mp-login-with-od`(23/25 完成,已合并进 main —— commits `ce2864b`/`38d1977`/`43bd025`)重做了小程序登录页视觉与手机号绑定交互,但遗留 2 项任务(6.4/6.5)因"当时环境没有运行中的微信开发者工具"被显式搁置:登录页 Step1/Step2 两态从未与 OD 原型 `mp-10-login.html` 做过实机视觉走查,也未纳入 C5 视觉验证 golden/geometry 集(`frontend/e2e/od-golden/` 现有 mp-01~mp-09 九屏,唯独缺 mp-10-login)。这意味着登录页——用户进入小程序的第一个品牌触点——是当前唯一一个完全没有回归防护网的屏,后续改动无法被 C5 自动感知。现在 `.claude/agents/mp-e2e-expert.md` + `frontend/e2e/tools/{preflight.sh,devtools-log-watch.cjs}` 工具链已合并到 main 且刚在同类任务(D5 图标视觉验证)中验证可用,阻塞条件已解除,应立即补上这个尾。

## What Changes

- 派发 `mp-e2e-expert` agent 对登录页 Step1(hero + 协议勾选 + 微信登录按钮)与 Step2(头像昵称 + 获取手机号引导 + 暂不绑定跳过链接)两态做 DevTools 实机走查截图,与 `mp-10-login.html` 原型逐项比对,记录并裁决任何明显偏离
- 补齐 `frontend/e2e/od-golden/mp-10-login.png` golden 截图,接入现有 `npm run test:visual` 感知 diff 流程(仿照 mp-01~mp-09 既有模式,不新建平行机制)
- 视登录页关键区域是否值得几何断言,补 `frontend/e2e/od-geometry/mp-10-login.json`,接入 `npm run test:geometry`
- 完成后回填 `align-mp-login-with-od/tasks.md` 的 6.4/6.5 复选框,注明由本 change 完成,使该 change 可视为 25/25 收尾(是否触发归档由用户决定)

## Capabilities

### New Capabilities

(无 —— 不引入新能力域)

### Modified Capabilities

- `visual-verification`:新增一条需求——多状态页面(同一路由存在多个视觉上有意义的独立态,如登录页 Step1/Step2)SHALL 按状态而非按路由生成 golden。现有 9 屏均为单状态页,从未需要这条约定;其余既有需求(golden 生成、几何主门、感知层、TDD 闭环)文本本身不变。

## Impact

- **新增文件**:`frontend/e2e/od-golden/mp-10-login.png`;视登录页是否值得几何断言而定,`frontend/e2e/od-geometry/mp-10-login.json`
- **修改文件**:`frontend/e2e/tools/visual-diff.cjs`(若需要显式登记新屏,视其现有实现是否已按目录扫描自动纳入而定)、`frontend/e2e/tools/geometry-diff.cjs`(如新增几何断言)、`openspec/changes/align-mp-login-with-od/tasks.md`(回填 6.4/6.5)
- **不涉及**:任何后端/业务逻辑代码;登录页 wxml/wxss/js 本身(除非走查发现真实偏离需要修复,那部分作为本 change 内的修复任务处理,不新开 change)
- **依赖的前置条件**(均已满足):main 上已有 `.claude/agents/mp-e2e-expert.md`、`frontend/e2e/tools/preflight.sh`、`frontend/e2e/tools/devtools-log-watch.cjs`;本机微信开发者工具已安装并可通过 `cli auto` 启动自动化端口
