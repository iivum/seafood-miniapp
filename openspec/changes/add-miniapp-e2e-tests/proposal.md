# Proposal: 小程序端 E2E 测试落地

## Why

当前测试金字塔缺顶层 E2E:后端 77 例覆盖 domain/application/infra,Jest 单元测试覆盖 components/hooks/utils,但**没有覆盖"用户从打开小程序到完成支付"的真实路径**。后果是核心用户旅程改版时全靠人工冒烟,极易回归。加 Phase 1 可观测性之后,定位这类回归仍需数十分钟,E2E 比监控更前置地阻断问题。

**为什么现在做**:Phase 1 已建好 `weapp-dev` MCP 自动化通道(`mp_ensureConnection` / `page_*` / `element_*` 工具集),不写 E2E 是浪费基建。

## What Changes

- **新增 E2E 基础设施**:`frontend/e2e/` 目录,使用 `miniprogram-automator` + `weapp-dev` MCP 驱动真实小程序运行时
- **编写 5 条核心用户旅程**:
  1. 登录 → 浏览商品 → 加购物车 → 下单 → 支付
  2. 商品搜索与分类筛选
  3. 订单列表 → 订单详情 → 取消订单
  4. 个人中心 → 地址管理
  5. 购物车多选 → 批量删除
- **CI 集成**:`.github/workflows/ci.yml` 新增 `e2e` job
  - 默认 `workflow_dispatch` + `schedule`(nightly) 触发,**不阻塞 PR**
  - 失败时上传截图到 GitHub Actions Artifacts
  - 触发条件可按需改 PR label(`run-e2e`)强制跑
- **测试数据隔离**:每次跑前通过 `backend/seed/seed.sh` 重置 MongoDB 到固定 fixture
- **本地运行文档**:`docs/testing/e2e.md`,开发者 5 分钟跑通

## Capabilities

- **New Capabilities**:
  - `miniapp-e2e-tests` — E2E 测试基础设施(运行器 + 报告 + 数据隔离)+ 5 条核心旅程用例集
- **Modified Capabilities**: 无(纯增量,不影响现有 spec 行为)

## Impact

### 新增文件
- `frontend/e2e/journeys/login-to-payment.spec.ts` 等 5 个 spec
- `frontend/e2e/setup/global-setup.ts`(启动 dev server + 启动小程序自动化会话)
- `frontend/e2e/setup/teardown.ts`(重置 MongoDB + 关闭会话)
- `frontend/e2e/utils/wait-helpers.ts`(微信特有等待封装)
- `docs/testing/e2e.md`

### 修改文件
- `frontend/package.json` — 加 `@miniprogram-automator/cli` 依赖 + `test:e2e` script
- `frontend/jest.config.ts` — 排除 `e2e/**` 目录(避免单元测试误跑)
- `frontend/tsconfig.json` — 单独给 e2e 一份 config
- `.github/workflows/ci.yml` — 新增 `e2e` job(独立 runner group)
- `backend/seed/seed.sh` — 加 `--reset-for-e2e` 模式(只保留 2 个固定用户 + 10 个商品)

### 依赖
- 新增 npm:`@miniprogram-automator/cli`(~50MB,需微信开发者工具 CLI)
- CI 需 macOS runner(macOS 14,微信开发者工具主要在 macOS 跑)
- Docker(本地起 backend + mongodb)

### 风险
- **微信开发者工具许可证**:CI 跑 automation 需要登录态,需用专用账号 + 长期 token 注入 secret
- **小程序的运行时稳定性**:automation API 偶有 flaky,需要重试 + 退避策略封装
- **耗时**:5 条旅程全套跑预计 8-15 分钟,需控制超时与并行度

### 前置依赖
- **Phase 1 #1 可观测性**(强烈建议先完成):E2E 失败时需要 tracing 串联前后端,否则定位慢
- 已具备:`weapp-dev` MCP、`backend/seed/seed.sh`、CI 基础设施
