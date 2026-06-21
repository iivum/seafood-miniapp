## Context

Sprint 2 单仓收敛(7 模块 → 1 模块)+ 2026-06-07 五连 CI 修复(commit 553f107, 0dd7330, 770313a, 80ccce0, 62bc835 等)落定后,`main` 分支已绿 1-2 周,期间没有新的 `fix(ci):` / `fix(smoke):` commit。本 change 借这个稳定窗口把 doc 跟上代码。

仓库现状(2026-06-08):
- 4 个 workflow:`ci.yml`、`native.yml`、`security.yml`、`cd.yml`(cd.yml 7-module 时代死代码,本 change 一并删,删后剩 3 个)
- `feature/refactor` 分支 5 commits 待 PR 合并
- `openspec/specs/` 有 4 个 capability:`admin-ui`、`auth`、`backend-api`、`mini-program`,无 `developer-docs`

doc drift 的根因不是"内容写错",是"没有 SOT 规则":4 个 capability spec 写的是"系统行为",没人规定"doc 自身如何写"。本次 change 用 6 件事同时治标(改错)与治本(立规则)。

## Goals / Non-Goals

**Goals:**
- `CLAUDE.md` 内部不再自相矛盾(L12 vs L108-127 消除)
- `CLAUDE.md` 的 CI/CD 描述与 `.github/workflows/*.yml` 实际数量一致(3 个,`cd.yml` 本 change 删)
- `.github/workflows/cd.yml` 删除,避免 dead workflow 误导读者
- `README.md` 重写,从 7-module 时代的过期内容改为单仓现状,符合 developer-docs spec 的 SOT 原则
- `openspec/specs/backend-api` 有显式 requirement 防止 OWASP Dep-Check 任务被加回 `check` 上(Sprint 2 已改实现,需 spec 跟上防回退)
- `openspec/specs/developer-docs` 存在,定义"doc 只 pointer,不复述事实"的 SOT 规则,后续 doc 类 change 的 `proposal.md` 必须回答 SOT 冲突自检

**Non-Goals:**
- 不为 `cd.yml` 写替代品。当前部署走本地 `docker-compose up -d`(见 CLAUDE.md "Docker 部署" 段);CD 是独立产品决策(单仓 CD 是不是要做 / 做什么 / 推到哪),不在 doc sync 范围。
- 不写新 `docs/CI-PIPELINES.md`。上次 `全部推迟` 的判断保留:yaml 注释本身已是 SOT,新建 doc 易漂移。
- 不动 `openspec/specs/admin-ui/spec.md` 的 `Purpose: [TBD — see change refactor-rust-rebuild-frontend]` 段 —— 那是 in-flight change 的责任。
- 不改任何代码、API、依赖。CI workflow 数量从 4 减为 3,实际行为不变(原 cd.yml 不会触发)。

## Decisions

### Decision 1: Vue admin-ui 段直接删,不做"指针化"

**选择**:整段删 L108-127(`### 管理后台 (Admin UI)` + Vue 技术栈 + 目录树 + BFF 端点)。

**备选 A**(否决):把 Vue 段改成 "已废弃,目标态见 admin-ui/spec.md" 的指针。
- 否决理由:L12 已说"Vue admin-ui 已废弃",L108-127 整段是重复表达同一信息(只是更详细)。指针化 = 同一信息两处,drift 半衰期短(下次有人改 L12 时新信息不再与 L108-127 一致)。
- 真正需要"详细描述"的是目标态 React 18 admin-ui —— 那是 `openspec/specs/admin-ui/spec.md` 的责任(已存在),CLAUDE.md 不应重复。

**备选 B**(否决):保留目录树,只删技术栈描述。
- 否决理由:目录树是"如何 clone 下来跑"的本地指引,不属于"项目级事实";本地指引放 `backend/admin-ui/README.md`(7-module 时代确实有,但已被 .gitignore 走)。CLAUDE.md 是项目级 doc,不应包含具体目录树。

### Decision 2: CLAUDE.md 的 CI/CD 描述合一处,保留末尾表

**选择**:删 `## 开发说明 → ### CI/CD`(L265-278),保留末尾 `## CI/CD`(L334-344),表保持 3 行(`cd.yml` 本 change 一并删,见 Decision 5)。

**备选 A**(否决):把两处 CI/CD 整段合到 `## 开发说明` 下面。
- 否决理由:末尾表离 "Git 工作流" / "性能要求" / "重要提示" 近,贴近"项目操作指引"段;移到 `## 开发说明` 中间反而割裂阅读流。Sprint 2 之后 CI 已是基础设施(不只开发期),`## 开发说明` 装不下。
- 备选 B(否决):两处都保留,L265-278 标 deprecated,留一行 pointer 指到末尾表。
  - 否决理由:deprecated 段 = 同一信息两处,违反 Decision 1 同样的 SOT 原则。既然末尾表更全,删 L265-278 是最干净的。

**附**:L265-278 的 L266 写 "Sprint 2 C5 §5.4 新增" 是历史叙述,新表不需要这种 "新增" 措辞(避免下次还得再标 "v2 新增"),直接写当前事实。

### Decision 3: Dep-Check 隔离写进 backend-api spec,不只是 build.gradle 注释

**选择**:在 `openspec/specs/backend-api/spec.md` 追加 `### Requirement: SCA 扫描隔离性`,固定两条:
- OWASP Dep-Check 任务不挂 `gradle check`
- 不在 ci.yml 跑,仅在 security.yml 跑

**备选 A**(否决):只在 `backend/build.gradle` 加注释。
- 否决理由:注释没有 enforcement。Sprint 2 当时改 build.gradle 时也是注释,但 PR review #8 之后才有人提"为啥 check 不跑 OWASP" — 注释没拦住反复讨论。spec 写进 scenario 后,任何后续 `check.dependsOn dependencyCheckAnalyze` 的 PR 都会被 reviewer 按 spec 拒绝。
- 备选 B(否决):写在 `openspec/changes/CHANGELOG.md` / docs/。
  - 否决理由:changelog 是历史叙述,不是契约。

### Decision 4: developer-docs 是 meta-capability(规则类),不是 feature spec

**选择**:新 `openspec/specs/developer-docs/spec.md` 只放一条 Requirement:`Single source of truth` —— 每条事实只在一处为权威来源(优先代码 / yaml 注释 / `openspec/specs/` 现有 capability),其他位置必须 pointer;所有后续 doc 类 change 的 `proposal.md` "Impact" 段必须回答"本 change 是否引入 SOT 冲突"。

**备选 A**(否决):把规则散到 4 个 capability spec 各自的 "维护注意" 段。
- 否决理由:散落 = 没人 grep 得到 = 没有 enforcement。集中到 developer-docs 后,后续 doc 类 change 的 reviewer 第一步 `openspec validate --change` 就会触发 spec 引用检查。
- 备选 B(否决):不写 spec,只在 CLAUDE.md "关键规则" 段加一句。
  - 否决理由:CLAUDE.md 是项目级 doc,本身就该被这个规则管。如果规则只活在 CLAUDE.md 里,等于"规则自己例外"。

**为什么是 meta**:developer-docs 不描述"系统行为",描述"doc 自身如何被书写"。spec 的 SHALL 主体不是 system,是"后续 change 的 proposal.md 必填字段"。这跟"auth" / "backend-api" 的"system SHALL" 形态不同,但 spec 框架接受 —— `### Requirement: ...` 后接任意 SHALL 主体。

### Decision 5: cd.yml 直接删,不 disable、不重写

**选择**:`rm .github/workflows/cd.yml`。

**备选 A**(否决):重命名为 `cd.yml.disabled` 或加 `if: false`。
- 否决理由:dead code 留仓库 = "以后可能会恢复"的暗示。CD 是否要做是产品决策,留 disabled 等于把这个决策悬置而不是关闭。git history 已能恢复(如有需要)。

**备选 B**(否决):重写为单仓版(push `seafood-backend:native` + 部署)。
- 否决理由:CD 是独立产品决策(目标环境是什么 / 触发条件 / SSH 还是 K8s)。doc sync 不应顺手做这个决策;若日后要恢复 CD,起一个独立 change。

**净效果**:CI workflow 数量 4 → 3。CLAUDE.md 表保持 3 行,自然 correct(配合 Decision 2)。

### Decision 6: README 重写原则 — 全 pointer,零 fact-copy

**选择**:新 README 内容严格按 developer-docs spec 的 SOT 规则写 —— 任何"事实"(版本号 / 端口 / 工具名 / 命令 / 流程)都用 pointer(`backend/build.gradle` / `Dockerfile` / `docker-compose.yml` / `.github/workflows/`)而非内联;README 自身只承载 GitHub 仓库首页需要的 5 类内容:

1. 项目名称 + 一行项目描述(对外 marketing 视角,不含版本)
2. 极简项目结构(只列 `frontend/` `backend/` `openspec/` `docs/` `CLAUDE.md`,不加注释)
3. 快速开始(具体命令,但命令来自 SOT 脚本;新 README 写的就是当前 docker-compose 启停命令,跟 CLAUDE.md L253-258 一致)
4. 文档链接(`CLAUDE.md` AI 视角、`docs/` 实际文档、`openspec/specs/` 行为契约)
5. License(MIT 保持)

**备选 A**(否决):保留 README 现有"开发状态表"(`购物车 ✅ 完成` / `商品列表 🚧 开发中` 等)。
- 否决理由:这些状态在 OpenSpec / `feature/refactor` PR 描述里有 SOT;README 的状态表既不是事实也不是契约,reviewer 不会去 README 校,3 个月内必漂。

**备选 B**(否决):保留 5 节详尽技术栈段。
- 否决理由:技术栈版本是 `build.gradle` / `package.json` 的事,README 不重复 —— 这正是 developer-docs spec 的 Scenario 2(技术栈版本必须 pointer)。

**风险**:README 重写 blast radius 大(影响 GitHub 仓库首页观感和 onboard 体验),通过 `git diff` 可审计、可回滚,blame 责任清晰。

## Risks / Trade-offs

- **[Risk] developer-docs spec 自身也是 doc,会不会自我违反** → 设计上 spec 文件本身就是 SOT 实体,跟 yaml 注释同级;规则不要求"自己也不能重复",只要求"指向自己时也要 pointer"(即其它 doc 提到 developer-docs 时给 spec.md 路径,不抄内容)。
- **[Risk] cd.yml 删除触发 reviewer 误读"CD 被砍了"** → 提交信息明确写 "removing dead 7-module workflow",PR body 注明当前部署走 `docker-compose up -d` 本地路径(已在 CLAUDE.md "Docker 部署" 段)。不引申"CD 是否需要做"的讨论。
- **[Risk] README 重写信息密度低,老用户找不到东西** → 5 节内容覆盖 GitHub 仓库首页的 5 类常见需求(项目名/结构/快速开始/链接/license);深度信息都在 CLAUDE.md / `docs/` / `openspec/specs/`。README 顶部一句 "更多信息见 [CLAUDE.md](CLAUDE.md)"。
- **[Risk] Sprint 2 之后 CI 还可能再 churn(比如 Dependabot 自动升 Spring Boot 改 Dep-Check 行为)** → developer-docs 的 SOT 规则 + backend-api 的 "Dep-Check 不挂 check" 契约是 spec-level,后续 PR review 有据可依;但若新 CI 工具引入新 SOT 冲突,需要新 change 补 developer-docs 的 ADDED Requirements 段(本 spec 设计为可扩展)。
- **[Risk] 删 L265-278 会让"用 git log 找 Sprint 2 C5 §5.4 native.yml 介绍"的 reviewer 找不到入口** → 末尾表的 `native.yml` 行保留 native pipeline 6 步描述(L270-274 内容),且链接到 `.github/workflows/native.yml`,信息量大于 L265-278 段。
- **[Risk] 本 change 把"developer-docs 必须 SOT" 写进 spec,但 spec 自身引入新 doc(`developer-docs/spec.md`)也增加了 SOT 实体** → 这是必要的代价(规则需要存身处);只要规则不被绕过,SOT 实体数量增加是良性的。

## Open Questions

- 无。当前 6 项决策 + 8 个 spec scenario 已闭合;cd.yml 不替代(Risks 已注 README blast radius),本 change 自洽。
