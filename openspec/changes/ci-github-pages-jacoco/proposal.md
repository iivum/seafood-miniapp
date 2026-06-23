# Proposal: CI GitHub Pages Jacoco 覆盖率报告托管

## Why

当前 CI 上传 Jacoco HTML/XML 报告为 GitHub Actions Artifact（保留 7 天），存在两个问题：

1. **不可链接**：PR review 无法直接打开 HTML 报告，只能下载 zip 后本地解压查看，审查摩擦高
2. **无趋势**：每次 CI 只有点状快照，看不到覆盖率随时间的变化趋势（哪个 sprint 引入了覆盖率下降）

GitHub Pages 发布可以：
- 每次 main 合并后自动更新，URL 固定可链接（`https://<org>.github.io/seafood-miniapp/coverage/`）
- 历史报告按 commit SHA 归档，支持对比任意两次

test-suite-roadmap §5 「可观测」子项的 GitHub Pages 缺口正是此需求。

## What Changes

**CI workflow（`ci.yml`）**：
- `backend` job（仅 push to main）：上传 Jacoco HTML 到 `gh-pages` 分支的 `coverage/` 目录
- 使用 `peaceiris/actions-gh-pages@v4` 或 `JamesIves/github-pages-deploy-action@v4`（零新增 secret，用 `GITHUB_TOKEN`）
- 报告路径：`gh-pages` 分支 `/coverage/latest/`（覆盖）+ `/coverage/<sha>/`（归档，保留 20 次）

**GitHub repo 设置（一次性手动）**：
- Settings → Pages → Source: `gh-pages` 分支 `/root` 目录

**PR comment 集成**：
- 在现有 Jacoco comment step 中补充 Pages 链接（`[查看完整 HTML 报告](https://...)`）

## Capabilities

- **New Capabilities**：
  - `ci/coverage-pages` — Jacoco HTML 报告 GitHub Pages 自动发布 + 历史归档
- **Modified Capabilities**：
  - `ci/backend-job` — push to main 时追加 Pages deploy step

## Impact

### 修改文件
- `.github/workflows/ci.yml`：backend job 增加 `Deploy coverage to GitHub Pages` step（仅 `if: github.event_name != 'pull_request'`）
- 无业务代码改动

### 新增文件
- `gh-pages` 分支（自动创建）：`coverage/latest/`、`coverage/<sha>/` 目录

### 风险
- `gh-pages` 分支每次 force-push，历史 git log 会被覆盖 → 用 `keep-files: true` 保留归档目录
- 覆盖率 HTML 含内部包名（`com.seafood.*`），信息暴露可接受（repo 已是私有）
- Pages build 额外耗时约 30s，不在 PR 关键路径上（仅 push main 触发）

### 前置依赖
- GitHub Pages 在 repo Settings 中手动启用（一次性）
- 无代码前置依赖
