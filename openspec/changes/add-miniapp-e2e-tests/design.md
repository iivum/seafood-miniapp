# Design: 小程序端 E2E 测试落地

## Context

测试金字塔当前结构:

- **后端 JUnit** :77 例,覆盖 `domain` / `application` / `infra` 三层
- **前端 Jest** :覆盖 `components` / `hooks` / `utils`
- **缺失**:用户从"打开小程序"到"完成支付"真实路径的自动化

后果:核心用户旅程(登录 → 浏览 → 加购 → 下单 → 支付)改版时全靠人工冒烟,易回归,定位慢,Phase 1 可观测性补齐后仍需数十分钟。

**关键基建已具备**:Phase 1 阶段已建好 `weapp-dev` MCP 自动化通道,提供 `mp_ensureConnection` / `page_*` / `element_*` / `mp_screenshot` 等工具集,自动化基建就绪。

## Goals / Non-Goals

**Goals:**

- 5 条核心用户旅程的端到端自动化(登录→下单→支付 / 商品搜索筛选 / 订单管理 / 地址管理 / 购物车批量)
- CI 默认 `schedule` nightly 触发,失败截图归档,**不阻塞 PR**
- 测试数据隔离(每次跑前重置 MongoDB 到固定 fixture)
- 本地开发者 5 分钟跑通
- 与现有 Jest 单元测试互不干扰(目录 + config 隔离)

**Non-Goals:**

- 性能/压力测试(独立 task,本项目暂不需要)
- 视觉回归测试(用 Chromatic / Percy 等专用工具,本次不做)
- 100% 路径覆盖(只覆盖 5 条核心 journey,其余靠单元测试)
- 跨端测试(只做微信小程序,不做支付宝/抖音/百度)
- 海外版 / 多语言(本项目只服务国内)

## Decisions

### 1. 工具链:`miniprogram-automator` (CI) + `weapp-dev` MCP (本地调试)

- **CI 不走 MCP** :MCP 是给 agent / 人用的,CI 用 `miniprogram-automator` npm 包直接调
- **本地手测 + 调试**:用 `mcp__weapp-dev__*` 工具(`mp_ensureConnection` / `page_tap` / `mp_screenshot` 等)
- **共用一份 `journeys/*.spec.ts` 源码**:用函数式封装,不绑死 MCP

### 2. 目录隔离:`frontend/e2e/` 与 `frontend/src/` 完全分开

```
frontend/
├── src/                      # 现有单元测试
│   └── features/.../         # Jest 跑这里
└── e2e/                      # 新增 E2E
    ├── journeys/             # 5 条 spec(用 .ts 写流程)
    ├── setup/                # global-setup / teardown
    ├── utils/                # wait / retry / screenshot 辅助
    ├── fixtures/             # 测试数据(选填,主要靠 seed.sh)
    └── miniprogram.config.ts # 微信开发者工具连接配置
```

- `jest.config.ts` 排除 `e2e/**`,避免单元测试误跑
- `tsconfig.json` 单开一份 `tsconfig.e2e.json`
- `package.json` 新增 scripts:`test:e2e` / `test:e2e:headed` / `test:e2e:report`

### 3. 触发策略:本期不强求集中 CI,探索本地自动跑机制

**为什么暂不上 GitHub Actions 集中跑?**

- macos-14 runner 成本约 5x linux(每分钟 $0.08),nightly 1 次约 $2-3
- 微信开发者工具 CI 登录态管理成本高(token 注入 + 过期轮换)
- 本期先验证"开发者本地能跑通"再考虑集中化

**候选方案**(待 Open Question 选 1):

- **A. pre-push git hook**:开发者 push 前自动跑 E2E,慢但强约束(可加 `git push --no-verify` 绕过)
- **B. self-hosted runner**:项目自维护 GitHub Actions runner,成本低但需自维护硬件/容器
- **C. 文档化要求**:`docs/testing/e2e.md` 写明"每次发版前本地跑一次 E2E",无强制约束
- **D. 阶段性 CI**:先文档化,Phase 3 流量起来后再评估 CI 集中跑

**本地统一入口**:

- `npm run test:e2e` 一键启动 `docker-compose.e2e` + 跑 5 条 journey
- 失败时 `screenshots/` 自动归档 + 控制台打印 `weapp-dev` MCP 调用栈
- 跑完 `npm run e2e:report` 生成 HTML 报告(Allure / 自建待定)

### 4. 数据隔离:`docker-compose.e2e.yml` + `seed.sh --reset-for-e2e`

- 独立 `docker-compose.e2e.yml`:独立的 mongodb volume(`mongodb_e2e_data`)+ 端口(27018)
- `seed.sh` 新增 `--reset-for-e2e` 模式:只保留 2 个固定用户 + 10 个商品 + 5 个分类(够 5 条 journey 跑)
- 每次跑前:`docker compose -f docker-compose.e2e.yml down -v && up -d` + `seed.sh --reset-for-e2e`

### 5. Flaky 缓解:helper 函数封装

- `waitForElement(selector, timeoutMs=5000)`:显式等待元素出现,避免 `tap` 时元素未就绪
- `tapWithRetry(selector, maxRetries=3)`:关键操作(下单、支付)重试
- `screenshotOnFail(name)`:失败时自动截图,命名 `<spec>_<step>_<timestamp>.png`
- `mp_getLogs({ clear: true })` 失败时收集 console 日志归档

## Risks / Trade-offs

| 风险 | 严重度 | 缓解 |
|---|---|---|
| 微信开发者工具 CI 登录态过期 | 高 | 本期不上 CI 集中跑,本地无此风险;若后续选 A/B 方案再处理 |
| 本地 E2E 难强制 / 跑通率低 | 中 | 先用 C 方案(文档化),统计实际跑通率;Phase 3 流量起来再评估 CI 集中跑 |
| 8-15 分钟耗时(本地) | 中 | 本地超时 30 分钟;优化方向:5 条 spec 拆 5 个 parallel job;只跑改动相关 journey |
| Flaky 测试 | 中 | 显式等待 + 重试 + 失败重跑整个 spec + 截图辅助定位 |
| 微信开发者工具版本不匹配 | 中 | lock 微信开发者工具 CLI 版本(用 `wecombot-cli` 锁 v1.06.x),本地/CI 一致 |
| E2E 调试体验 | 中 | 截图 + console log + DOM dump 三件套失败时归档 |

## Open Questions

1. **E2E 报告格式**:Allure / 自建 HTML / 纯 log? — 5 条 journey 跑通后定,候选 Allure(社区成熟)
2. **失败时自动开 GitHub issue 还是发飞书告警?** — 默认飞书(已有告警通道),不开 issue 避免噪声
3. **Staging 环境 E2E vs 本地 docker E2E?** — 本期只做本地 docker,后续按需加 staging(对接真实微信号)
4. **支付环节 mock 微信 vs 微信沙箱?** — 短期 mock 微信返回(走 `WECHAT_ENABLED=false` dev 通道),sandbox 待调研
5. **是否纳入覆盖率统计?** — 不纳入(行覆盖/分支覆盖对 E2E 无意义,跟"journey 覆盖"独立)
6. **CI runner 形态**:见 §3 Decisions 候选方案 A / B / C / D,待定 — 默认先走 C(文档化要求)验证 E2E 价值,再决定是否上 CI 集中跑
