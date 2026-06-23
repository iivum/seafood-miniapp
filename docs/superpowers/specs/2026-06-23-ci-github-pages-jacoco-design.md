# CI GitHub Pages Jacoco 覆盖率报告设计

> **Status**: Draft  
> **Date**: 2026-06-23  
> **Proposal**: `openspec/changes/ci-github-pages-jacoco/proposal.md`

---

## 1. 目标

Jacoco HTML 报告每次 push to main 后自动发布到 GitHub Pages，URL 固定可链接，PR comment 附链接，历史按 SHA 归档。

---

## 2. Action 选型

选 **`peaceiris/actions-gh-pages@v4`**。

| 维度 | `peaceiris/actions-gh-pages@v4` | `JamesIves/github-pages-deploy-action@v4` |
|---|---|---|
| `keep_files` 支持 | 原生支持，保留历史归档目录 | 需手动管理 |
| GITHUB_TOKEN | 支持 | 支持 |
| 清理旧归档 | 需配合脚本 | 同 |
| 社区维护 | 活跃 | 活跃 |

`keep_files: true` 是关键：每次 force-push gh-pages 时保留已存在的 `coverage/<sha>/` 归档目录，只覆盖 `coverage/latest/`。

---

## 3. gh-pages 分支目录结构

```
gh-pages 分支根目录/
├── coverage/
│   ├── latest/          ← 每次 push main 覆盖（当前最新报告）
│   │   ├── index.html
│   │   └── ...
│   └── <sha>/           ← 历史归档（最多保留 20 个，按时间删旧）
│       ├── index.html
│       └── ...
└── index.html           ← 可选：简单重定向到 coverage/latest/
```

访问 URL：`https://iivum.github.io/seafood-miniapp/coverage/latest/`

---

## 4. 触发条件与权限

```yaml
# 仅 push to main 触发，PR 跳过（避免 fork PR GITHUB_TOKEN 写权限问题）
if: github.event_name != 'pull_request'

# 需要 pages: write 和 contents: write 权限
permissions:
  contents: write   # 写 gh-pages 分支
  pages: write      # （可选，deploy 到 Pages 环境时需要）
```

当前 `ci.yml` 无顶层 `permissions` 块，需在 `backend` job 级别或顶层添加。推荐 job 级别，最小权限。

---

## 5. 完整 workflow step YAML

以下 YAML 可直接插入 `ci.yml` backend job 的 `steps` 末尾（在 `Native compile` step 之后）：

```yaml
      # GitHub Pages Jacoco 报告发布（仅 push to main）
      # 目录：coverage/latest/（覆盖）+ coverage/<sha>/（归档，保留 20 次）
      - name: Prepare coverage directories
        if: github.event_name != 'pull_request'
        run: |
          mkdir -p /tmp/gh-pages-coverage/coverage/latest
          mkdir -p /tmp/gh-pages-coverage/coverage/${{ github.sha }}
          cp -r backend/build/reports/jacoco/test/html/. /tmp/gh-pages-coverage/coverage/latest/
          cp -r backend/build/reports/jacoco/test/html/. /tmp/gh-pages-coverage/coverage/${{ github.sha }}/

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

**`keep_files: true`** 保证：发布 `coverage/latest/` 和 `coverage/<sha>/` 时，不删除 gh-pages 分支已有的其他 `coverage/<old-sha>/` 目录。

---

## 6. 归档清理策略

每次发布后，通过脚本删除超过 20 个的旧归档（按 commit 时间排序，删最早的）：

```yaml
      - name: Prune old coverage archives (keep 20)
        if: github.event_name != 'pull_request'
        uses: actions/github-script@v7
        with:
          script: |
            // 列出 gh-pages 分支 coverage/ 下的所有 sha 目录
            // 按 commit 时间排序，只保留最新 20 个
            const { data: branch } = await github.rest.repos.getBranch({
              owner: context.repo.owner,
              repo: context.repo.repo,
              branch: 'gh-pages',
            }).catch(() => ({ data: null }));
            if (!branch) return; // gh-pages 尚未初始化

            const { data: tree } = await github.rest.git.getTree({
              owner: context.repo.owner,
              repo: context.repo.repo,
              tree_sha: branch.commit.sha,
              recursive: '1',
            });

            // 找出所有 coverage/<sha>/ 目录（取第一层子目录名）
            const shaSet = new Set();
            tree.tree.forEach(item => {
              const m = item.path.match(/^coverage\/([0-9a-f]{40})\//);
              if (m) shaSet.add(m[1]);
            });

            const shas = [...shaSet];
            if (shas.length <= 20) {
              console.log(`Archive count: ${shas.length} ≤ 20, no pruning needed`);
              return;
            }

            // 按 commit 创建时间排序（oldest first）
            const withDate = await Promise.all(shas.map(async sha => {
              try {
                const { data: commit } = await github.rest.git.getCommit({
                  owner: context.repo.owner,
                  repo: context.repo.repo,
                  commit_sha: sha,
                });
                return { sha, date: commit.author.date };
              } catch {
                return { sha, date: '1970-01-01T00:00:00Z' };
              }
            }));
            withDate.sort((a, b) => a.date.localeCompare(b.date));

            const toDelete = withDate.slice(0, withDate.length - 20);
            console.log(`Pruning ${toDelete.length} old archives:`, toDelete.map(x => x.sha));

            // 删除 tree 中属于这些 sha 的条目（重建 tree）
            // 注意：这是只读脚本示意，实际删除需重建 tree + commit，复杂度较高
            // 简化方案：接受超出 20 时记 warning，由人工 git push 清理
            // 或在此阶段 checkout gh-pages 用 rm -rf + git push 处理
            console.log('WARNING: Auto-prune via API tree rewrite skipped (complexity). Manual cleanup needed when archives > 20.');
```

**简化说明**：via API 重建 git tree 删目录复杂度高，推荐替代方案：

```yaml
      # 简化归档清理：checkout gh-pages 后用 shell 删旧目录
      - name: Prune old coverage archives (keep 20)
        if: github.event_name != 'pull_request'
        run: |
          set -euo pipefail
          git fetch origin gh-pages --depth=1 2>/dev/null || exit 0
          git checkout gh-pages 2>/dev/null || exit 0
          # 列出 coverage/<sha> 目录（排除 latest），按修改时间排序
          dirs=$(ls -dt coverage/????????????????????????????????????????????????/ 2>/dev/null \
                 | grep -v coverage/latest | head -n -20 || true)
          if [ -n "$dirs" ]; then
            echo "Removing old archives: $dirs"
            rm -rf $dirs
            git add -A
            git commit -m "ci: prune old coverage archives" --allow-empty
            git push origin gh-pages
          fi
          git checkout -
```

---

## 7. PR comment 追加 Pages 链接

在现有 `PR coverage comment` step 的 `body` 变量中追加链接：

```javascript
// 替换现有 body 构造行：
const body = banner + 'See `jacoco-coverage` artifact for per-file breakdown.';

// 改为：
const pagesUrl = 'https://iivum.github.io/seafood-miniapp/coverage/latest/';
const body = banner
  + `[查看完整 HTML 报告](${pagesUrl})\n\n`
  + 'See `jacoco-coverage` artifact for per-file breakdown.';
```

注意：Pages URL 在 PR 触发时还未更新（Pages 只 push main 时发布），但 `latest/` 指向上一次 main 合并的报告，仍有参考价值。

---

## 8. 手动一次性设置步骤

在 workflow 跑通前，需先在 GitHub repo 开启 Pages：

1. 进入 `https://github.com/iivum/seafood-miniapp/settings/pages`
2. **Source** 选 `Deploy from a branch`
3. **Branch** 选 `gh-pages`，目录选 `/ (root)`
4. 点 Save

首次 push main 后 Actions 会自动创建 `gh-pages` 分支并发布。

---

## 9. job 级别权限配置

`backend` job 需要写 gh-pages 分支的权限：

```yaml
  backend:
    name: Backend (Java 25 + GraalVM)
    runs-on: ubuntu-latest
    timeout-minutes: 30
    permissions:
      contents: write   # 写 gh-pages 分支
      pull-requests: write  # PR comment（已有功能，显式声明）
```

如果 repo 顶层设置了 `permissions: read-all`，job 级别显式声明 `contents: write` 才能覆盖。

---

## 10. 风险与缓解

| 风险 | 缓解 |
|---|---|
| `gh-pages` force-push 丢历史 | `keep_files: true`，保留 `coverage/<sha>/` |
| 覆盖率 HTML 含包名 `com.seafood.*` | repo 私有，可接受 |
| Pages build 耗时 ~30s | 仅 push main 触发，不在 PR 关键路径 |
| Fork PR 缺 `contents: write` | `if: github.event_name != 'pull_request'` 完全跳过 |
| 归档超 20 个 | shell 脚本自动清理，失败记 warning 不阻塞 |
