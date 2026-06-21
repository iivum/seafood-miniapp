## Why

`feature/refactor` 单仓改造已落定、Sprint 2 CI 修复(2026-06-07 五连 commit)也合并入 `main` 1-2 周,期间 `native.yml` + `security.yml` 连续绿、没新的 `fix(ci):` commit。doc 跟上代码的安全窗口打开。

仓库里发现 6 处与当前代码不一致的 doc drift / 过期工件,范围都明确:

1. `CLAUDE.md` L12 说 "Vue admin-ui 已废弃",但 L108-127 整段又在描述 Vue admin-ui 目录结构 — 内部自相矛盾,误导后续 reviewer。
2. `CLAUDE.md` 的 CI/CD 描述分两处: `### 开发说明 → CI/CD`(L265-278,只讲 `native.yml`)和文件末 `## CI/CD`(L334-344,讲 3 个 workflow 表)。Sprint 2 之后两者都过期。
3. `openspec/specs/backend-api/spec.md` 没有"OWASP Dep-Check 不挂 `gradle check`、仅在 `security.yml` 跑"的契约 — Sprint 2 把 Dep-Check 从 `check` task 上摘下来时没回写到 spec,易回退。
4. 仓库没有"developer-docs" capability 的 spec — 即没有强约束"doc 只能 pointer 不能 copy 事实",后续 doc 类 change 容易复发第 1/2 项那种 drift。
5. `.github/workflows/cd.yml` 是 7 模块时代的死代码(`workflow_run.workflows: ["CI Build & Test"]` 引用的 workflow 名已不存在;`matrix.service` 含 6 个已归档 service;`JAVA_VERSION: '17'`;`deploy-to-server` 健康检查 8080-8083 端口也跟单仓的 8080 不一致)。当前不会触发,留着误导读者以为有 CD。
6. `README.md` 整段过期(项目描述是 "Spring Cloud 微服务";项目结构树是 7 模块;tech stack 是 Java 17+ / Spring Boot 3.x / Spring Cloud / Eureka;文档链接指向已不存在的 `SPEC.md` / `TODO.md` / `ARCHITECTURE.md`)。GitHub 仓库首页看到的就是这份过期 README,影响 onboard 体验。

## What Changes

- 删 `CLAUDE.md` L108-127 整个 `### 管理后台 (Admin UI)` 段(含 Vue 技术栈 + `backend/admin-ui/vue/` 目录树 + BFF 端点)。L12 已说明 admin-ui 目标态为 React 18 + shadcn/ui + Vite,该 Vue 段是 7 模块时代的残留。
- 修 `CLAUDE.md` CI/CD 描述:删 L265-278 重复的 `### CI/CD` 子段(在 `## 开发说明` 下面),保留文件末 L334-344 表(3 个 workflow,与 `.github/workflows/` 实际数量一致)。不扩表(`cd.yml` 本 change 一并删,见下条)。
- 删 `.github/workflows/cd.yml` 整个文件。理由:7-module 时代死代码,`workflow_run.workflows: ["CI Build & Test"]` 引用的 workflow 名已不存在;`matrix.service` 6 个 service 已归档;`deploy-to-server` 健康检查端口 8080-8083 与单仓 8080 不一致;`JAVA_VERSION: '17'`、GraalVM `23` 与当前 `25` 不一致。本 change 一并清理,不留死工件。
- 重写 `README.md`:从 GitHub 仓库首页视角(对外,不是 AI 视角的 CLAUDE.md)重写,项目描述改为单 Spring Boot 模块 + 微信小程序,项目结构树改为当前 `frontend/` + `backend/` + `openspec/`,技术栈通过 `pointer`(指向 `backend/build.gradle` / `frontend/package.json` / `.github/workflows/`)而非内联,文档链接改到真实存在的 `CLAUDE.md` / `docs/` / `openspec/`。具体编写原则见 design.md Decision 6。
- 在 `openspec/specs/backend-api/spec.md` 追加 `### Requirement: SCA 扫描隔离性` 段,固化"OWASP Dep-Check 任务不挂 `gradle check`、不在 ci.yml 跑、仅在 `security.yml` 跑;其 binding 一旦被加回 `check.dependsOn` 即视为回退"。
- 新建 `openspec/specs/developer-docs/spec.md`,定义 `### Requirement: Single source of truth` —— 每条事实(doc claim)只在一处为权威来源(优先代码 / yaml 注释 / `openspec/specs/`),其他位置必须用 pointer(link、文件名、line)而不是 copy。要求所有后续 doc 类 change 的 `proposal.md` 在"Impact"段回答"本 change 是否引入 SOT 冲突"。

## Capabilities

### New Capabilities

- `developer-docs`: 定义仓库内部"开发者文档"这一 capability 的行为契约 —— 主要是 SOT 规则(doc 只 pointer,不复述事实),以及 `openspec` workflow 自身如何消费这个规则。

### Modified Capabilities

- `backend-api`: 在现有 spec 末尾追加 `Requirement: SCA 扫描隔离性` 子段。该 REQUIREMENT 改变 spec 行为契约(从此禁止 Dep-Check 挂 `check`),所以是 Modified,不是只改 implementation 细节。

## Impact

- 修改文件:
  - `CLAUDE.md`(2 处编辑:删 L108-127、删 L265-278 重复 CI/CD 子段)
  - `openspec/specs/backend-api/spec.md`(末尾追加 1 个 Requirement + 3 个 Scenario)
- 重写文件:
  - `README.md`(整篇重写;原 110 行 7-module 时代内容全删,新内容走 design.md Decision 6 原则)
- 删除文件:
  - `.github/workflows/cd.yml`(7-module 时代死代码,见 What Changes 第 3 条)
- 新增文件:
  - `openspec/changes/sync-docs-with-latest-code/specs/developer-docs/spec.md`(随 apply 落到 `openspec/specs/developer-docs/spec.md`)
- 不涉及代码、API、依赖。CI workflow 数量从 4 减为 3,实际行为不变(原 cd.yml 不会触发)。
- 风险面:README 重写 + cd.yml 删除 = 2 处大改动,影响 GitHub 仓库首页观感(README)和 CI 现状理解。两者都通过 `git diff` 可审计、可回滚,blame 责任清晰。

## Out-of-scope 决策记录

- 不写"CI 详细 reference doc"(如 `docs/CI-PIPELINES.md`)。上次 `全部推迟` 的判断保留:`.github/workflows/*.yml` 注释本身已是 SOT,新建 doc 易漂移,本 change 不开此路径。
- 不为 cd.yml 写替代品。当前部署走本地 `docker-compose up -d`(见 CLAUDE.md "Docker 部署" 段);CD 是独立产品决策,不在 doc sync 范围。
- 不动 `openspec/specs/admin-ui/spec.md` 的 `Purpose: [TBD — see change refactor-rust-rebuild-frontend]` 段 —— 那是另一条 in-flight change 处理的,本 change 改了反而撞车。
