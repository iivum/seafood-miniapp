## 1. CLAUDE.md 编辑(2 处)

- [x] 1.1 删 `CLAUDE.md` L108-127 整个 `### 管理后台 (Admin UI)` 段(含 Vue 技术栈 + 目录树 + BFF 端点列表)。改完 L12 的"Vue admin-ui 已废弃"成为唯一信息源
- [x] 1.2 删 `CLAUDE.md` L265-278 的 `### CI/CD` 子段(在 `## 开发说明` 下面,只讲 native.yml 的那处)
- [x] 1.3 校 `CLAUDE.md` L334-344 CI/CD 表:保持 3 行(`cd.yml` 本 change 一并删,不再列入),native.yml 行的描述保留 6 步 pipeline(Sprint 2 之后的当前事实,不写 "新增")
- [x] 1.4 校读 L1-160 整段,确认删完之后没有"详见下方" / "如上" 之类的悬空引用

## 2. OpenSpec spec 落地

- [x] 2.1 跑 `openspec apply sync-docs-with-latest-code`,把 `specs/backend-api/spec.md` 的 ADDED Requirements 合入 `openspec/specs/backend-api/spec.md`,把 `specs/developer-docs/spec.md` 落到 `openspec/specs/developer-docs/spec.md`
- [x] 2.2 跑 `openspec validate --strict` 确认 0 errors(包括现有 4 个 spec 仍然 valid,新加的 developer-docs 满足 schema)

## 3. .github/workflows/cd.yml 删除

- [x] 3.1 `rm .github/workflows/cd.yml`
- [x] 3.2 跑 `ls .github/workflows/` 确认剩 3 个(ci.yml / native.yml / security.yml)
- [x] 3.3 跑 `git log --all --full-history -- .github/workflows/cd.yml` 留 audit trail(给 reviewer 展示历史 commit 路径)

## 4. README.md 重写

- [x] 4.1 备份当前 README.md 内容到 `git show HEAD:README.md`(便于 diff 引用)
- [x] 4.2 按 design.md Decision 6 原则重写 5 节内容:项目名 + 一行描述 / 极简结构 / 快速开始 / 文档链接 / License
  - 不写"开发状态表"
  - 不写技术栈版本号,改 pointer 到 `backend/build.gradle` / `frontend/package.json`
  - 文档链接改到 `CLAUDE.md` / `docs/` / `openspec/specs/`
- [x] 4.3 自检:重写后 `git grep "Spring Cloud\|Eureka\|product-service\|order-service\|gateway\|discovery-service\|ARCHITECTURE\|SPEC.md\|TODO.md" README.md` 应该 0 命中(7-module 时代关键字清空)

## 5. 自检(实施者 + reviewer 各跑一次)

- [x] 5.1 `git grep -n "backend/admin-ui/vue"` 应该 0 命中(目录树引用清空)。L12 的"Vue admin-ui 已废弃" 是 SOT 描述,保留
- [x] 5.2 `git grep -n "cd\.yml"` 在 `.github/workflows/` 之外应该 0 命中(没有 stale 引用)
- [x] 5.3 跑 `./gradlew check` 确认 backend test 仍然过(本 change 不改 backend 代码,但要确认 backend 任何隐式影响 —— 比如 spec 文件夹改名是否触发 ArchUnit 规则)
- [x] 5.4 读 `openspec/specs/backend-api/spec.md` 末尾,确认 "SCA 扫描隔离性" Requirement 在最末,Scenario 数 = 3
- [x] 5.5 读 `openspec/specs/developer-docs/spec.md` 全文,确认 1 个 Requirement + 5 个 Scenario,格式跟现有 4 个 spec 一致
- [x] 5.6 浏览器(或本地)打开 `README.md` 渲染视图,GitHub 仓库首页视角过一遍:项目名/描述/结构/快速开始/链接全可见,没视觉断点

## 6. Commit + PR

- [x] 6.1 **Commit 1**:`chore(ci): remove dead 7-module cd.yml workflow`
  - 仅 `rm .github/workflows/cd.yml`,无其它改动
  - body 简述:该 workflow 引用已不存在的 `CI Build & Test` workflow 名 + 6 个已归档 service + JDK 17,实际不触发,清理死工件
  - footer `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`
- [x] 6.2 **Commit 2**:`docs(claude): 同步 CLAUDE.md,删 Vue admin-ui 段 + 重复 CI/CD 子段`
  - 仅改 `CLAUDE.md`,2 处删除
  - body 引用 proposal.md What Changes 第 1-2 条
- [x] 6.3 **Commit 3**:`docs(specs): 加 SCA 扫描隔离性 + 新建 developer-docs spec`
  - 改 `openspec/specs/backend-api/spec.md`(append 1 Requirement + 3 Scenario)
  - 新增 `openspec/specs/developer-docs/spec.md`(1 Requirement + 5 Scenario)
  - body 引用 proposal.md What Changes 第 5-6 条
- [x] 6.4 **Commit 4**:`docs(readme): 重写 README,消除 7-module 时代过期内容`
  - 仅改 `README.md`,整篇重写
  - body 引用 design.md Decision 6 原则
  - 5 个 commit 按 (ci→claude→specs→readme) 顺序:让 reviewer 顺着看过去,先看到最小最有把握的删除,再看到 doc,最后到 README 视觉改动
- [x] 6.5 PR 描述结尾 `## Test plan` 段:
  - "已跑 `openspec validate --strict` 0 errors"
  - "已跑 `./gradlew check` 通过"
  - "已确认 `git grep` 0 悬空引用(7-module 时代关键字)"
  - "已浏览器渲染 README 过 5 节内容"
  - "已列 4 commit 顺序 + 各 commit 独立可回滚"
- [x] 6.6 PR 标题:`docs: 同步 CLAUDE.md / README / spec,清理 dead cd.yml workflow`
- [x] 6.7 提 PR 到 `feature/refactor` 分支(不开到 main —— main 当前绿,`feature/refactor` 5 commits 仍待 PR 合并,本 change 走同一 PR 队列)
- [x] 6.8 PR 描述结尾加 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`

## 7. 后续(非本 change 范围,留任务跟踪)

- [x] 7.1 观察未来 2 周:developer-docs spec 的 SOT 规则是否被 PR review 自然执行(没自然执行就在本 spec 追加更严格的 enforcement scenario)
- [x] 7.2 观察 README 重写后是否有"找不到 X 信息"的反馈(若有,在 CLAUDE.md 加 1 句 pointer,不在 README 重复)
- [x] 7.3 若日后需要 CD,起独立 change(本 change 不预判)
