# PR TDD Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 强制每个触碰源码的 PR 必须填写 ≥ 2 个具体 checkbox 的 Test plan，防止测试债务重新累积。

**Architecture:** 新增 `.github/PULL_REQUEST_TEMPLATE.md` 在创建 PR 时自动注入模板；新增 `.github/workflows/pr-lint.yml` 在 PR 事件时检查 PR body，先做路径豁免判断，再校验 Test plan 章节。两个文件完全独立，无运行时依赖。

**Tech Stack:** GitHub Actions YAML、`actions/github-script@v7`（Node.js 内联脚本）、GitHub REST API（`pulls.listFiles`、`context.payload`）

## Global Constraints

- 触发事件：仅 `pull_request`（opened / edited / synchronize / reopened），不在 `push` 上运行
- 路径豁免白名单：`src/`、`frontend/`、`admin-ui/src/`（前缀匹配 `filename`）
- Test plan 合格标准：`## Test plan` 章节存在 + checkbox（`- [ ]` 或 `- [x]`）≥ 2 条 + 不全为空或字面量 `TODO`
- 失败必须有明确中文错误信息，指引开发者如何修复
- 所有 `run:` 块不得插值 `${{ github.event.* }}`（注入防护）
- Workflow 文件使用 `actions/github-script@v7`（与 `ci.yml` 保持一致）

---

## File Map

| 操作 | 路径 | 说明 |
|---|---|---|
| Create | `.github/PULL_REQUEST_TEMPLATE.md` | PR 创建时自动填充的模板 |
| Create | `.github/workflows/pr-lint.yml` | CI lint workflow |

---

### Task 1: PR 模板

**Files:**
- Create: `.github/PULL_REQUEST_TEMPLATE.md`

**Interfaces:**
- Produces: PR 创建 UI 自动填充的模板结构，`## Test plan` 章节供 Task 2 的 workflow 解析

- [ ] **Step 1: 创建 PR 模板文件**

```bash
# 确认 .github/ 目录存在
ls .github/
```

Expected: 看到 `workflows/`、`dependabot.yml` 等现有文件

- [ ] **Step 2: 写入模板内容**

创建 `.github/PULL_REQUEST_TEMPLATE.md`，内容如下（逐字复制，不改标题大小写）：

```markdown
## Summary

<!-- 1-3 条要点说明本 PR 做了什么 -->

## Changes

<!-- 改动清单，按模块/层列出 -->

## Test plan

<!-- ≥ 2 个 checkbox，不能全是 TODO -->
- [ ] 
- [ ] 

## Screenshots

<!-- 如有 UI 改动，粘贴截图；无则删除本节 -->

---
🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

- [ ] **Step 3: 手动验证模板格式**

在 GitHub UI 上打开 "New Pull Request"（或使用 `gh pr create` 进入编辑器），确认 body 已预填上述内容。

如果没有预填，检查文件路径是否精确为 `.github/PULL_REQUEST_TEMPLATE.md`（全大写，无多余空格）。

- [ ] **Step 4: Commit**

```bash
git add .github/PULL_REQUEST_TEMPLATE.md
git commit -m "chore: 新增 PR 模板 — Summary/Changes/Test plan/Screenshots"
```

---

### Task 2: pr-lint.yml Workflow

**Files:**
- Create: `.github/workflows/pr-lint.yml`

**Interfaces:**
- Consumes: PR body（`context.payload.pull_request.body`）、PR 改动文件列表（`github.rest.pulls.listFiles`）
- Produces: CI job `PR Lint (Test plan gate)` — 路径豁免时 neutral，Test plan 合格时 green，不合格时 red + 中文错误信息

- [ ] **Step 1: 本地验证 JS 校验逻辑（可选但推荐）**

在项目根目录临时创建 `lint-pr-body.test.mjs`，运行以测试核心逻辑：

```javascript
// lint-pr-body.test.mjs
// 使用 node --test 内置测试框架（Node 18+）
import { strict as assert } from 'assert';
import { test } from 'node:test';

function validateTestPlan(body) {
  if (!body || !body.includes('## Test plan')) {
    return { ok: false, msg: '❌ PR lint failed: 未找到 ## Test plan 章节\n   提示：使用 PR 模板（创建 PR 时自动填充）' };
  }
  const sections = body.split(/\n(?=## )/);
  const testPlanSection = sections.find(s => s.startsWith('## Test plan')) || '';
  const checkboxes = (testPlanSection.match(/^- \[[ x]\] .*/gm) || []);
  if (checkboxes.length < 2) {
    return { ok: false, msg: `❌ PR lint failed: Test plan 需要 ≥ 2 个 checkbox（当前 ${checkboxes.length} 个）\n   提示：在 ## Test plan 下添加至少 2 条 "- [ ] 具体测试项"` };
  }
  const allTodo = checkboxes.every(cb => {
    const text = cb.replace(/^- \[[ x]\] /, '').trim();
    return text === '' || /^todo$/i.test(text);
  });
  if (allTodo) {
    return { ok: false, msg: '❌ PR lint failed: Test plan 中所有 checkbox 均为空或 TODO\n   提示：填写具体测试步骤，例如 "- [ ] 登录后可见仪表盘"' };
  }
  return { ok: true, msg: `✅ PR lint passed: Test plan 包含 ${checkboxes.length} 个具体 checkbox` };
}

test('body 为 null → 失败', () => {
  const r = validateTestPlan(null);
  assert.equal(r.ok, false);
  assert.ok(r.msg.includes('未找到'));
});

test('无 Test plan 章节 → 失败', () => {
  const r = validateTestPlan('## Summary\nhello');
  assert.equal(r.ok, false);
  assert.ok(r.msg.includes('未找到'));
});

test('只有 1 个 checkbox → 失败', () => {
  const r = validateTestPlan('## Test plan\n- [ ] 只有一条\n');
  assert.equal(r.ok, false);
  assert.ok(r.msg.includes('1 个'));
});

test('2 个 checkbox 全为 TODO → 失败', () => {
  const r = validateTestPlan('## Test plan\n- [ ] TODO\n- [ ] TODO\n');
  assert.equal(r.ok, false);
  assert.ok(r.msg.includes('均为空或 TODO'));
});

test('2 个 checkbox 全为空 → 失败', () => {
  const r = validateTestPlan('## Test plan\n- [ ] \n- [ ] \n');
  assert.equal(r.ok, false);
  assert.ok(r.msg.includes('均为空或 TODO'));
});

test('2 个具体 checkbox → 通过', () => {
  const r = validateTestPlan('## Test plan\n- [ ] 登录后可见仪表盘\n- [x] 退出后跳转登录页\n');
  assert.equal(r.ok, true);
});

test('混合：1 个 TODO + 1 个具体 → 通过（不全是 TODO）', () => {
  const r = validateTestPlan('## Test plan\n- [ ] TODO\n- [ ] 具体步骤\n');
  assert.equal(r.ok, true);
});

test('Test plan 后有其他章节 → 正确提取', () => {
  const body = '## Summary\nhello\n## Test plan\n- [ ] step1\n- [ ] step2\n## Screenshots\n无';
  const r = validateTestPlan(body);
  assert.equal(r.ok, true);
});
```

运行：

```bash
node --test lint-pr-body.test.mjs
```

Expected：8 个测试全部 `pass`。

- [ ] **Step 2: 确认测试全部通过后删除临时文件**

```bash
rm lint-pr-body.test.mjs
```

- [ ] **Step 3: 创建 pr-lint.yml**

创建 `.github/workflows/pr-lint.yml`，内容如下：

```yaml
name: PR Lint (Test plan gate)

on:
  pull_request:
    types: [opened, edited, synchronize, reopened]

env:
  FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: 'true'

jobs:
  pr-lint:
    name: PR Lint (Test plan gate)
    runs-on: ubuntu-latest
    timeout-minutes: 3
    steps:
      - name: Validate Test plan
        uses: actions/github-script@v7
        with:
          script: |
            // 1. 路径豁免检查
            const { data: files } = await github.rest.pulls.listFiles({
              owner: context.repo.owner,
              repo: context.repo.repo,
              pull_number: context.payload.pull_request.number,
              per_page: 100,
            });
            const GUARDED = ['src/', 'frontend/', 'admin-ui/src/'];
            const touchesCode = files.some(f => GUARDED.some(p => f.filename.startsWith(p)));
            if (!touchesCode) {
              console.log('ℹ️  PR lint skipped: 改动不包含 src/、frontend/、admin-ui/src/ 下的文件');
              return;
            }

            // 2. 提取 PR body
            const body = context.payload.pull_request.body || '';

            // 3. ## Test plan 章节必须存在
            if (!body.includes('## Test plan')) {
              core.setFailed('❌ PR lint failed: 未找到 ## Test plan 章节\n   提示：使用 PR 模板（创建 PR 时自动填充）');
              return;
            }

            // 4. 提取 Test plan 章节（到下一个 ## 或文档末尾）
            const sections = body.split(/\n(?=## )/);
            const testPlanSection = sections.find(s => s.startsWith('## Test plan')) || '';

            // 5. checkbox 数量 ≥ 2
            const checkboxes = (testPlanSection.match(/^- \[[ x]\] .*/gm) || []);
            if (checkboxes.length < 2) {
              core.setFailed(`❌ PR lint failed: Test plan 需要 ≥ 2 个 checkbox（当前 ${checkboxes.length} 个）\n   提示：在 ## Test plan 下添加至少 2 条 "- [ ] 具体测试项"`);
              return;
            }

            // 6. 不全为空或 TODO
            const allTodo = checkboxes.every(cb => {
              const text = cb.replace(/^- \[[ x]\] /, '').trim();
              return text === '' || /^todo$/i.test(text);
            });
            if (allTodo) {
              core.setFailed('❌ PR lint failed: Test plan 中所有 checkbox 均为空或 TODO\n   提示：填写具体测试步骤，例如 "- [ ] 登录后可见仪表盘"');
              return;
            }

            console.log(`✅ PR lint passed: Test plan 包含 ${checkboxes.length} 个具体 checkbox`);
```

- [ ] **Step 4: 验证 YAML 语法合法**

```bash
python3 -c "import yaml, sys; yaml.safe_load(open('.github/workflows/pr-lint.yml'))" && echo "YAML OK"
```

Expected：`YAML OK`（无报错）

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/pr-lint.yml
git commit -m "feat(ci): PR lint — Test plan gate (pr-lint.yml)"
```

---

### Task 3: 端到端验证 + design.md §5 验收打勾

**Files:**
- Modify: `docs/superpowers/specs/2026-06-22-pr-tdd-gate-design.md`（验收清单打勾）
- Modify: `openspec/changes/test-suite-roadmap/design.md`（§5 "可持续"勾选）

**Interfaces:**
- Consumes: Task 1（PR 模板已在 GitHub），Task 2（pr-lint.yml 已推送并在 CI 中运行）

- [ ] **Step 1: Push 到远端，触发 CI**

```bash
git push
```

- [ ] **Step 2: 创建"坏 PR"验证失败路径**

用 `gh` 创建一个只改文档的 PR（应豁免）：

```bash
# 先在本地做一个无害改动
echo "<!-- test -->" >> docs/superpowers/specs/2026-06-22-pr-tdd-gate-design.md
git add docs/superpowers/specs/2026-06-22-pr-tdd-gate-design.md
git commit -m "test: 触发 PR lint 豁免测试（将删除）"
git push
gh pr create --title "test: pr-lint 豁免验证" --body "只改文档，应跳过 lint" --draft
```

Expected：PR Lint job 输出 `ℹ️  PR lint skipped`，status green。

- [ ] **Step 3: 验证源码改动的 PR 强制校验**

在同一个 draft PR 上用 `gh pr edit` 把 body 改成不含 Test plan 的内容（或直接在 GitHub UI 上编辑），然后再加一个 `src/` 下的文件改动。

Expected：PR Lint job 输出 `❌ PR lint failed: 未找到 ## Test plan 章节`，status red。

- [ ] **Step 4: 关闭/删除测试 PR，还原临时 commit**

```bash
gh pr close <PR_NUMBER>
git revert HEAD --no-edit
git push
```

- [ ] **Step 5: 在 design.md 打验收勾**

打开 `docs/superpowers/specs/2026-06-22-pr-tdd-gate-design.md`，将 §7 验收清单中已验证的项改为 `[x]`：

```markdown
- [x] 创建改动 `src/` 的 PR，body 无 Test plan → CI job 红
- [x] 创建改动 `src/` 的 PR，body 有 ≥ 2 个具体 checkbox → CI job 绿
- [x] 创建纯文档改动 PR（只改 `docs/`）→ CI job 跳过（neutral）
- [x] PR body 只有 1 个 checkbox → CI job 红，错误信息明确
- [x] 所有 checkbox 均为 `- [ ] TODO` → CI job 红
```

- [ ] **Step 6: 在 test-suite-roadmap design.md §5 打可持续勾**

打开 `openspec/changes/test-suite-roadmap/design.md`，将 §5 对应行改为：

```markdown
- [x] **可持续**:每个新功能 PR 默认 TDD(PR 模板 check);覆盖率 + mutation score 趋势卡点
```

- [ ] **Step 7: 在 test-suite-roadmap tasks.md 打 T11 勾（如 §5 全部完成）**

检查 `openspec/changes/test-suite-roadmap/tasks.md` 中 T11，若 §5 所有条目全勾，将 T11 标为完成：

```markdown
- [x] T11 整体验收清单(本路线图 §5)全部勾完
```

- [ ] **Step 8: Commit**

```bash
git add docs/superpowers/specs/2026-06-22-pr-tdd-gate-design.md \
        openspec/changes/test-suite-roadmap/design.md \
        openspec/changes/test-suite-roadmap/tasks.md
git commit -m "chore: PR TDD gate 验收打勾 + test-suite-roadmap §5 可持续勾选"
```

---

## 完成判据

- [ ] `gh pr create` 创建 PR 时 body 自动包含 Summary / Changes / Test plan / Screenshots 章节
- [ ] 改动 `src/`、`frontend/`、`admin-ui/src/` 的 PR，若 Test plan 不合格 → CI 红
- [ ] 纯文档/配置改动的 PR → CI job 跳过（不阻塞）
- [ ] `pr-lint.yml` job 名称为 `PR Lint (Test plan gate)`（供 Branch protection 规则引用）
