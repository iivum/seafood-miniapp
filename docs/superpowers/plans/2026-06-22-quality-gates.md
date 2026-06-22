# Quality Gates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 admin-ui 加 coverage thresholds 并移除 CI `continue-on-error`；加 pre-commit hook 让本地提交跑后端质量门禁。

**Architecture:** admin-ui 有 8 个 TDD RED 测试（`ad-od-design.test.tsx`，v2.2 OD 对齐路线图），需先把它们从主 test run 分离，获得干净的 coverage baseline，再设 thresholds。pre-commit hook 用 `.githooks/` 机制（无需 husky/npm），通过 `git config core.hooksPath` 激活，一次性脚本 `scripts/install-hooks.sh` 供每个开发者执行。

**Tech Stack:** Vitest 2.1.3（v8 coverage）、Gradle 9.x、bash git hooks

## Global Constraints

- admin-ui 测试用 `vitest run`，不是 jest
- Gradle 门禁命令：`./gradlew check -PexcludeTags=docker`（跳过 Testcontainers IT）
- `.githooks/pre-commit` 必须 `chmod +x`
- `vite.config.ts` 中 coverage thresholds 用 `thresholds` 字段（vitest 2.x 语法）
- TDD RED 测试文件路径：`admin-ui/src/__tests__/ad-od-design.test.tsx`

---

### Task 1: 分离 TDD RED 测试 + 获取 coverage baseline

**Files:**
- Modify: `admin-ui/vite.config.ts:40-57`（test.exclude 加 OD 设计测试）

**Interfaces:**
- Produces: 干净的 coverage baseline（全绿 135 个测试），供 Task 2 设 threshold

- [ ] **Step 1: 在 vite.config.ts 的 test 块加 exclude**

修改 `admin-ui/vite.config.ts`，在 `test:` 块加 `exclude`：

```ts
test: {
  globals: true,
  environment: 'jsdom',
  setupFiles: ['./src/test/setup.ts'],
  css: false,
  exclude: [
    '**/node_modules/**',
    '**/dist/**',
    'src/__tests__/ad-od-design.test.tsx',   // TDD RED — v2.2 OD 对齐路线图，单独跑
  ],
  coverage: {
    provider: 'v8',
    reporter: ['text', 'html', 'json-summary'],
    include: ['src/**/*.{ts,tsx}'],
    exclude: [
      'src/test/**',
      'src/**/*.test.{ts,tsx}',
      'src/**/index.ts',
      'src/types/**',
      'src/main.tsx',
    ],
  },
},
```

- [ ] **Step 2: 跑 coverage 获取 baseline 数字**

```bash
cd admin-ui && npm run test:coverage 2>&1 | tail -20
```

预期：135 passed，0 failed。coverage 报告输出在终端。记录 Stmts/Branch/Funcs/Lines 数字。

- [ ] **Step 3: 验证 coverage-summary.json 生成**

```bash
ls admin-ui/coverage/coverage-summary.json
node -e "const d=require('./admin-ui/coverage/coverage-summary.json').total; console.log(Object.keys(d).map(k=>k+':'+d[k].pct+'%').join(' '))"
```

预期：打印 4 个维度的覆盖率百分比。

- [ ] **Step 4: commit**

```bash
git add admin-ui/vite.config.ts
git commit -m "test(admin-ui): 分离 TDD RED OD 对齐测试出主 test run

ad-od-design.test.tsx 是 v2.2 路线图 RED 阶段（OD 元素未实现），
单独跑 vitest run src/__tests__/ad-od-design.test.tsx。
主 test run 恢复全绿，为加 coverage threshold 铺路。

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: 加 coverage thresholds + 移除 CI continue-on-error

**Files:**
- Modify: `admin-ui/vite.config.ts:40-57`（coverage.thresholds）
- Modify: `.github/workflows/ci.yml`（删 admin-ui job 的 `continue-on-error: true`）

**Interfaces:**
- Consumes: Task 1 产出的 baseline coverage 百分比
- Produces: admin-ui CI job 变成真正的质量门

- [ ] **Step 1: 根据 baseline 设 thresholds（取实际值 -5%，向下取整到 5 的倍数）**

在 Task 1 Step 3 获得数字后，在 `admin-ui/vite.config.ts` 的 `coverage:` 块加：

```ts
// 示例：若实际 lines=68%、branches=55%、functions=70%、statements=68%
// thresholds 设为 lines:60, branches:50, functions:65, statements:60
thresholds: {
  lines: <actual - 5，向下取整到5的倍数>,
  branches: <actual - 5，向下取整到5的倍数>,
  functions: <actual - 5，向下取整到5的倍数>,
  statements: <actual - 5，向下取整到5的倍数>,
},
```

- [ ] **Step 2: 本地验证 thresholds 不阻塞（应通过）**

```bash
cd admin-ui && npm run test:coverage 2>&1 | grep -E "ERROR|threshold|Coverage"
```

预期：无 threshold 错误，coverage 通过。

- [ ] **Step 3: 移除 ci.yml 中 admin-ui job 的 continue-on-error**

修改 `.github/workflows/ci.yml`，找到：

```yaml
admin-ui:
  name: Admin UI (Vitest,可选)
  runs-on: ubuntu-latest
  timeout-minutes: 15
  continue-on-error: true   # §9 还没落地
```

删除 `continue-on-error: true` 行和其注释。

- [ ] **Step 4: commit**

```bash
git add admin-ui/vite.config.ts .github/workflows/ci.yml
git commit -m "feat(admin-ui): 加 coverage thresholds + CI 去掉 continue-on-error

vitest v8 coverage thresholds: lines/stmts/funcs/branches 各 baseline-5%。
admin-ui CI job 不再 continue-on-error — 测试失败真正阻塞合并。

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: pre-commit hook（后端质量门禁）

**Files:**
- Create: `.githooks/pre-commit`
- Create: `scripts/install-hooks.sh`
- Modify: `package.json`（根，加 `prepare` 脚本）

**Interfaces:**
- Produces: 每次 `git commit` 自动跑 `./gradlew check -PexcludeTags=docker`

- [ ] **Step 1: 创建 .githooks/pre-commit**

```bash
#!/usr/bin/env bash
set -euo pipefail

# 后端质量门禁 — 每次提交前跑（跳过需 Docker 的 Testcontainers IT）
echo "▶ pre-commit: 后端 check (excludeTags=docker)…"
cd "$(git rev-parse --show-toplevel)/backend"
./gradlew --no-daemon check -PexcludeTags=docker
echo "✅ 后端 check 通过"
```

- [ ] **Step 2: 创建 scripts/install-hooks.sh**

```bash
#!/usr/bin/env bash
set -euo pipefail
REPO=$(git rev-parse --show-toplevel)
git config core.hooksPath .githooks
chmod +x "$REPO/.githooks/pre-commit"
echo "✅ git hooks 已安装 (core.hooksPath=.githooks)"
```

- [ ] **Step 3: 更新根 package.json 加 prepare 提示**

在根 `package.json` 的 `scripts` 加：

```json
"prepare": "git config core.hooksPath .githooks && chmod +x .githooks/pre-commit || true"
```

- [ ] **Step 4: 本地激活并验证**

```bash
bash scripts/install-hooks.sh
git config core.hooksPath   # 应输出 .githooks
```

- [ ] **Step 5: 测试 hook 触发（不实际 commit）**

```bash
bash .githooks/pre-commit
```

预期：后端 check 跑完，最终打印 `✅ 后端 check 通过`。

- [ ] **Step 6: commit**

```bash
git add .githooks/pre-commit scripts/install-hooks.sh package.json
git commit -m "feat(dev-dx): pre-commit hook 跑后端 check（excludeTags=docker）

.githooks/pre-commit + scripts/install-hooks.sh。
新 clone 后跑 bash scripts/install-hooks.sh 或 npm run prepare 激活。

Co-Authored-By: Claude <noreply@anthropic.com>"
```
