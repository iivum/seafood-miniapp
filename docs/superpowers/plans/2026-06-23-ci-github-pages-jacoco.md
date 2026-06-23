# CI GitHub Pages Jacoco 报告发布计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Jacoco HTML 报告 push to main 后自动发布到 GitHub Pages，PR comment 附链接

**Architecture:** ci.yml backend job 追加 deploy step，仅 push 触发

**Tech Stack:** GitHub Actions, Jacoco, GitHub Pages

## Global Constraints
- 仅 push to main 触发（PR 不部署，避免 fork PR 权限问题）
- 使用 GITHUB_TOKEN，不需要额外 secret
- 归档最多保留 20 个历史快照（按 SHA 命名）
- 报告 URL：https://iivum.github.io/seafood-miniapp/coverage/latest/

---

## Task 1：ci.yml backend job 增加 deploy-pages steps

**File**: `.github/workflows/ci.yml`  
**Location**: `backend` job 的 `steps` 末尾，`Native compile` step 之后  
**设计参考**: `docs/superpowers/specs/2026-06-23-ci-github-pages-jacoco-design.md` §4-6

### Steps

- [ ] 在 `backend` job 顶部添加 `permissions` 块（`contents: write` + `pull-requests: write`）：

  ```yaml
    permissions:
      contents: write
      pull-requests: write
  ```

- [ ] 在 `Native compile` step **之后**追加 `Prepare coverage directories` step：

  ```yaml
        - name: Prepare coverage directories
          if: github.event_name != 'pull_request'
          run: |
            mkdir -p /tmp/gh-pages-coverage/coverage/latest
            mkdir -p /tmp/gh-pages-coverage/coverage/${{ github.sha }}
            cp -r backend/build/reports/jacoco/test/html/. /tmp/gh-pages-coverage/coverage/latest/
            cp -r backend/build/reports/jacoco/test/html/. /tmp/gh-pages-coverage/coverage/${{ github.sha }}/
  ```

- [ ] 追加 `Deploy coverage to GitHub Pages` step：

  ```yaml
        - name: Deploy coverage to GitHub Pages
          if: github.event_name != 'pull_request'
          uses: peaceiris/actions-gh-pages@v4
          with:
            github_token: ${{ secrets.GITHUB_TOKEN }}
            publish_dir: /tmp/gh-pages-coverage
            destination_dir: .
            keep_files: true
            commit_message: "ci: update coverage report [${{ github.sha }}]"
  ```

- [ ] 追加 `Prune old coverage archives (keep 20)` step：

  ```yaml
        - name: Prune old coverage archives (keep 20)
          if: github.event_name != 'pull_request'
          run: |
            set -euo pipefail
            git fetch origin gh-pages --depth=1 2>/dev/null || exit 0
            git worktree add /tmp/gh-pages-worktree gh-pages 2>/dev/null || exit 0
            cd /tmp/gh-pages-worktree
            dirs=$(ls -dt coverage/????????????????????????????????????????????????/ 2>/dev/null \
                   | grep -v 'coverage/latest' | tail -n +21 || true)
            if [ -n "$dirs" ]; then
              echo "Removing old archives: $dirs"
              rm -rf $dirs
              git add -A
              git diff --cached --quiet || git commit -m "ci: prune old coverage archives"
              git push origin gh-pages
            fi
            cd -
            git worktree remove /tmp/gh-pages-worktree --force
  ```

  > 注：`ls -dt` 按目录修改时间倒序，`tail -n +21` 跳过最新 20 个，删旧的。

### 验收标准
- `ci.yml` lint 通过（`yamllint` 或 GitHub Actions 语法检查）
- push main 后 Actions `backend` job 包含 3 个新 step

---

## Task 2：PR comment step 追加 Pages URL

**File**: `.github/workflows/ci.yml`  
**Location**: `PR coverage comment` step 的 `script` 块  
**设计参考**: `docs/superpowers/specs/2026-06-23-ci-github-pages-jacoco-design.md` §7

### Steps

- [ ] 找到 `PR coverage comment` step 中的以下行（约第 125 行）：

  ```javascript
  const body = banner + 'See `jacoco-coverage` artifact for per-file breakdown.';
  ```

- [ ] 替换为：

  ```javascript
  const pagesUrl = 'https://iivum.github.io/seafood-miniapp/coverage/latest/';
  const body = banner
    + `[查看完整 HTML 报告](${pagesUrl})\n\n`
    + 'See `jacoco-coverage` artifact for per-file breakdown.';
  ```

### 验收标准
- PR 触发 CI 后，PR comment 包含 `查看完整 HTML 报告` 链接
- 链接指向 `https://iivum.github.io/seafood-miniapp/coverage/latest/`

---

## Task 3：一次性手动设置 + 验证

**前置条件**：Task 1 和 Task 2 已完成并 push 到 main

### Steps

- [ ] **手动开启 GitHub Pages**（一次性，需有 repo Admin 权限）：
  1. 进入 `https://github.com/iivum/seafood-miniapp/settings/pages`
  2. Source 选 `Deploy from a branch`
  3. Branch 选 `gh-pages`，目录选 `/ (root)`
  4. 点 Save

- [ ] **触发验证 push**：确认 Task 1 的 commit 已在 main 分支，此步骤由 Task 1 完成时的 push 自动触发

- [ ] **确认 Actions 执行结果**：
  - 打开 `https://github.com/iivum/seafood-miniapp/actions`
  - 找到最新 `CI` workflow run（push main 触发）
  - backend job 的 `Deploy coverage to GitHub Pages` step 应显示绿色

- [ ] **确认 Pages 可访问**：
  - 打开 `https://iivum.github.io/seafood-miniapp/coverage/latest/`
  - 应显示 Jacoco HTML 覆盖率报告首页（类/包列表）

- [ ] **确认归档存在**：
  - 打开 `https://iivum.github.io/seafood-miniapp/coverage/<commit-sha>/`（用本次 push 的 SHA）
  - 应与 `latest/` 内容相同

- [ ] **确认 PR comment 更新**（在下一个 PR 的 CI 上验证）：
  - PR 的覆盖率 comment 中含 `查看完整 HTML 报告` 链接

### 验收标准
- Pages URL 返回 200，显示 Jacoco HTML 报告
- `gh-pages` 分支存在 `coverage/latest/` 和 `coverage/<sha>/` 目录
- PR comment 附 Pages 链接

---

## 参考文档

- 设计文档：`docs/superpowers/specs/2026-06-23-ci-github-pages-jacoco-design.md`
- Proposal：`openspec/changes/ci-github-pages-jacoco/proposal.md`
- 现有 CI：`.github/workflows/ci.yml`（backend job，约第 45-149 行）
- peaceiris/actions-gh-pages 文档：https://github.com/peaceiris/actions-gh-pages
